package app.teletype.sdk.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.teletype.sdk.config.APPEAL_STATUS_CLOSED
import app.teletype.sdk.config.CONFIG_LIFETIME_MS
import app.teletype.sdk.config.DEFAULT_CHAT_TITLE
import app.teletype.sdk.config.DEFAULT_MODE
import app.teletype.sdk.config.DEFAULT_PRIMARY_COLOR
import app.teletype.sdk.config.MAX_UPLOAD_BYTES
import app.teletype.sdk.config.RECEIVED_ACK_TIMEOUT_MS
import app.teletype.sdk.config.StorageKeys
import app.teletype.sdk.config.TeletypeEnvironment
import app.teletype.sdk.config.TeletypeMode
import app.teletype.sdk.config.WsEvent
import app.teletype.sdk.config.getEnvironment
import app.teletype.sdk.config.isAllowedUploadFile
import app.teletype.sdk.config.messageTypeFromFile
import app.teletype.sdk.i18n.TeletypeStrings
import app.teletype.sdk.i18n.TeletypeTranslations
import app.teletype.sdk.i18n.resolveStrings
import app.teletype.sdk.json.camelKeys
import app.teletype.sdk.json.optArr
import app.teletype.sdk.json.optBool
import app.teletype.sdk.json.optIntOrNull
import app.teletype.sdk.json.optStr
import app.teletype.sdk.json.snakeKeys
import app.teletype.sdk.types.AdditionalPersonInfo
import app.teletype.sdk.types.ChannelMessenger
import app.teletype.sdk.types.ChatBackground
import app.teletype.sdk.types.ConversationRating
import app.teletype.sdk.types.DateObject
import app.teletype.sdk.types.FormType
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.Message
import app.teletype.sdk.types.MessageStatus
import app.teletype.sdk.types.MessageType
import app.teletype.sdk.types.Operator
import app.teletype.sdk.types.UploadedAttachment
import app.teletype.sdk.types.WidgetConfigData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class TeletypeConfig(
    val teletypeExternalId: String,
    val mode: TeletypeMode = DEFAULT_MODE,
    val environment: TeletypeEnvironment? = null,
    val storage: KeyValueStore? = null,
    val soundEnabled: Boolean = false,
    val locale: String? = null,
    val translations: TeletypeTranslations? = null,
    val title: String? = null,
    val primaryColor: String? = null,
    val chatBackground: ChatBackground = ChatBackground.DEFAULT,
    val device: Map<String, String> = emptyMap(),
)

data class TeletypeState(
    val ready: Boolean = false,
    val opened: Boolean = false,
    val connection: String = "disconnected",
    val messages: List<Message> = emptyList(),
    val operators: List<Operator> = emptyList(),
    val channels: List<ChannelMessenger> = emptyList(),
    val config: WidgetConfigData? = null,
    val unreadCount: Int = 0,
    val operatorTyping: Boolean = false,
    val draft: String = "",
    val title: String = DEFAULT_CHAT_TITLE,
    val primaryColor: String = DEFAULT_PRIMARY_COLOR,
    val chatBackground: ChatBackground = ChatBackground.DEFAULT,
    val locale: String = "en",
    val strings: TeletypeStrings = app.teletype.sdk.i18n.EN_STRINGS,
)

/**
 * Headless chat engine. Owns the socket + REST lifecycle and conversation
 * state. Compose UI is a thin subscriber on top of [stateFlow].
 */
class TeletypeClient(
    context: Context,
    config: TeletypeConfig,
) {
    val externalId: String = config.teletypeExternalId
    val env: TeletypeEnvironment = getEnvironment(config.mode).let { base ->
        if (config.environment == null) base
        else TeletypeEnvironment(
            mode = config.environment.mode.ifBlank { base.mode },
            version = config.environment.version.ifBlank { base.version },
            apiUrl = config.environment.apiUrl.ifBlank { base.apiUrl },
            wsUrl = config.environment.wsUrl.ifBlank { base.wsUrl },
            assetsUrl = config.environment.assetsUrl.ifBlank { base.assetsUrl },
            apiVersion = config.environment.apiVersion.ifBlank { base.apiVersion },
            landingUrl = config.environment.landingUrl.ifBlank { base.landingUrl },
        )
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api = ApiClient(env, externalId, appContext, debug = env.mode == "dev")
    private val socket = SocketClient(env.wsUrl, debug = env.mode == "dev")
    private val storage = Storage(externalId, config.storage, appContext)
    private val emitter = TypedEventEmitter()

    private var translations: TeletypeTranslations? = config.translations
    private var localeValue: String = resolveStrings(config.locale, config.translations).first
    private var stringsValue: TeletypeStrings = resolveStrings(config.locale, config.translations).second
    private var titleValue: String = config.title ?: DEFAULT_CHAT_TITLE
    private var primaryColorValue: String? = config.primaryColor
    private var chatBackgroundValue: ChatBackground = config.chatBackground
    private var soundEnabled: Boolean = config.soundEnabled
    private val device: Map<String, String> = config.device.ifEmpty {
        mapOf(
            "os" to "Android ${Build.VERSION.RELEASE}",
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    private var ready = false
    private var opened = false
    private var connection = "disconnected"
    private var messages: List<Message> = emptyList()
    private var operators: List<Operator> = emptyList()
    private var channels: List<ChannelMessenger> = emptyList()
    private var widgetConfig: WidgetConfigData? = null
    private var unreadCount = 0
    private var operatorTyping = false
    private var draft = ""
    private var personId = ""
    private var appealIdValue = ""
    private var personData: Map<String, Any?>? = null
    private var personAdditionalInfo = AdditionalPersonInfo()
    private var greetingSent = false
    private var messageWasSent = false
    private var banned = false
    private var pendingMessages = mutableListOf<Message>()
    private var messagesInPending = mutableListOf<Message>()
    private val receivedTimeouts = mutableMapOf<String, Runnable>()
    private var initialized = false
    private var listenersBound = false

    private val _state = MutableStateFlow(buildState())
    val stateFlow: StateFlow<TeletypeState> = _state.asStateFlow()
    val state: TeletypeState get() = _state.value
    val appealId: String get() = appealIdValue
    val locale: String get() = localeValue
    val strings: TeletypeStrings get() = stringsValue
    val title: String get() = titleValue
    val primaryColor: String
        get() = primaryColorValue ?: widgetConfig?.backgroundColor ?: DEFAULT_PRIMARY_COLOR
    val chatBackground: ChatBackground get() = chatBackgroundValue

    fun getMessages(): List<Message> = messages

    fun setLocale(locale: String? = null, translations: TeletypeTranslations? = null) {
        if (translations != null) {
            this.translations = (this.translations ?: emptyMap()) + translations
        }
        val resolved = resolveStrings(locale, this.translations)
        localeValue = resolved.first
        stringsValue = resolved.second
        emitter.emit("localeChanged", localeValue)
        publishState()
    }

    fun setTitle(title: String) {
        titleValue = title
        emitter.emit("appearanceChanged")
        publishState()
    }

    fun setPrimaryColor(color: String?) {
        primaryColorValue = color
        emitter.emit("appearanceChanged")
        publishState()
    }

    fun setChatBackground(background: ChatBackground) {
        chatBackgroundValue = background
        emitter.emit("appearanceChanged")
        publishState()
    }

    fun <T> on(event: String, listener: (T) -> Unit): () -> Unit = emitter.on(event, listener)
    fun onUnit(event: String, listener: () -> Unit): () -> Unit = emitter.onUnit(event, listener)

    suspend fun init() {
        if (initialized) return
        initialized = true
        withContext(Dispatchers.IO) {
            storage.hydrate(
                listOf(
                    StorageKeys.PERSON_ID,
                    StorageKeys.APPEAL_ID,
                    StorageKeys.CONFIG,
                    StorageKeys.CONFIG_SAVE_TIME,
                    StorageKeys.PENDING_MESSAGES,
                    StorageKeys.POPUP_STATE,
                    StorageKeys.WIDGET_INTERACTED,
                ),
            )
        }
        personId = storage.getString(StorageKeys.PERSON_ID)
        appealIdValue = storage.getString(StorageKeys.APPEAL_ID)
        pendingMessages = messagesFromArray(storage.getJsonArray(StorageKeys.PENDING_MESSAGES))
            .map { it.copy(status = MessageStatus.Failed) }
            .toMutableList()

        try {
            val response = withContext(Dispatchers.IO) { loadConfig() }
            applyConfig(response)
        } catch (err: Throwable) {
            emitter.emit("error", err)
        }

        if (appealIdValue.isNotBlank()) {
            loadHistory()
        }

        ready = true
        emitter.emit("ready")
        publishMessages()
        publishState()

        if (appealIdValue.isNotBlank() || pendingMessages.isNotEmpty()) {
            ensureConnected()
        }
    }

    private fun loadConfig(): JSONObject {
        val saveTime = storage.getLong(StorageKeys.CONFIG_SAVE_TIME, 0L)
        val cached = storage.getJson(StorageKeys.CONFIG)
        val expired = System.currentTimeMillis() - saveTime > CONFIG_LIFETIME_MS
        if (cached != null && !expired) return cached
        val fresh = api.getConfig()
        if (isUsableConfigResponse(fresh)) {
            storage.set(StorageKeys.CONFIG, fresh)
            storage.set(StorageKeys.CONFIG_SAVE_TIME, System.currentTimeMillis())
        }
        return fresh
    }

    private fun applyConfig(response: JSONObject) {
        if (!isUsableConfigResponse(response)) return
        val dataObj = response.opt("data") as? JSONObject ?: return
        val data = configFromJson(dataObj)
        widgetConfig = data
        operators = data.operators.filter { it.name.isNotBlank() }
        channels = data.channels
        data.soundEnabled?.let { soundEnabled = it }
        emitter.emit("config", data)
        publishState()
    }

    private suspend fun loadHistory() {
        try {
            val res = withContext(Dispatchers.IO) { api.getHistory(appealIdValue) }
            val history = messagesFromArray(res.optArr("data")).map { m ->
                m.copy(
                    unread = if (m.seen) false else m.unread,
                    emojiOnly = isEmojiOnly((m.message ?: "").trim()),
                )
            }
            setMessages(history.reversed() + pendingMessages, fromHistory = true)
        } catch (err: Throwable) {
            emitter.emit("error", err)
        }
    }

    fun ensureConnected() {
        socket.ensureCreated()
        if (!listenersBound) {
            bindSocket()
            listenersBound = true
        }
        socket.connect()
    }

    suspend fun waitUntilConnected(timeoutMs: Long = 15_000): Unit = suspendCoroutine { cont ->
        if (connection == "connected") {
            cont.resume(Unit)
            return@suspendCoroutine
        }
        var off: () -> Unit = {}
        val timer = Runnable {
            off()
            if (cont.context[kotlin.coroutines.ContinuationInterceptor] != null) {
                try {
                    cont.resumeWithException(IllegalStateException("Socket connect timeout"))
                } catch (_: Exception) {
                }
            }
        }
        off = on<String>("connectionChanged") { state ->
            if (state != "connected") return@on
            mainHandler.removeCallbacks(timer)
            off()
            try {
                cont.resume(Unit)
            } catch (_: Exception) {
            }
        }
        mainHandler.postDelayed(timer, timeoutMs)
        ensureConnected()
        if (connection == "connected") {
            mainHandler.removeCallbacks(timer)
            off()
            try {
                cont.resume(Unit)
            } catch (_: Exception) {
            }
        }
    }

    private fun bindSocket() {
        socket.off()
        val debug = env.mode == "dev"

        socket.on(WsEvent.CONNECT) {
            runOnMain {
                if (debug) Log.d("teletype", "[ws] • connect")
                connection = "connected"
                emitter.emit("connectionChanged", "connected")
                publishState()
                handleConnect()
            }
        }
        socket.on(WsEvent.DISCONNECT) {
            runOnMain {
                if (debug) Log.d("teletype", "[ws] • disconnect")
                connection = "disconnected"
                emitter.emit("connectionChanged", "disconnected")
                publishState()
            }
        }
        socket.on(WsEvent.INIT_DATA_TO_PERSON) { args ->
            val data = socket.firstObject(args)
            runOnMain {
                data.optStr("person_id")?.let { id ->
                    personId = id
                    storage.set(StorageKeys.PERSON_ID, personId)
                    personData?.let { setUserData(it) }
                }
                data.optStr("appeal_id")?.let { id ->
                    if (appealIdValue.isBlank() && messagesInPending.isNotEmpty()) {
                        messagesInPending.forEach { emitMessage(it) }
                        messagesInPending.clear()
                    }
                    setAppealId(id)
                }
            }
        }
        socket.on(WsEvent.MESSAGE_FROM_CLIENT) { args ->
            val raw = camelKeys(socket.firstObject(args)) as JSONObject
            runOnMain {
                val data = messageFromJson(raw)
                handleIncomingMessage(data)
                emitter.emit("messageReceived", data)
            }
        }
        socket.on(WsEvent.OPERATOR_TYPING) {
            runOnMain { setOperatorTyping(true) }
        }
        socket.on(WsEvent.OPERATOR_STOP_TYPING) {
            runOnMain { setOperatorTyping(false) }
        }
        socket.on(WsEvent.OPERATOR_STATUS_CHANGE) { args ->
            val data = socket.firstObject(args)
            runOnMain {
                val clientId = data.optStr("client_id")
                val online = data.optBool("online")
                val idx = operators.indexOfFirst { it.id == clientId }
                if (idx >= 0) {
                    operators = operators.toMutableList().also {
                        it[idx] = it[idx].copy(isOnline = online)
                    }
                    publishState()
                }
            }
        }
        socket.on(WsEvent.APPEAL_STATUS_CHANGE) { args ->
            val data = socket.firstObject(args)
            runOnMain {
                val status = data.optIntOrNull("status")
                if (status == APPEAL_STATUS_CLOSED && !banned) {
                    createRatingForm()
                    emitter.emit("dialogClosed", data)
                }
            }
        }
        socket.on(WsEvent.RECEIVED) { args ->
            val data = socket.firstObject(args)
            runOnMain { handleReceived(data) }
        }
        socket.on(WsEvent.SEEN) { args ->
            val data = socket.firstObject(args)
            runOnMain { handleSeen(data) }
        }
    }

    private fun handleConnect() {
        if (personId.isNotBlank()) {
            mainHandler.postDelayed({
                socket.emit(
                    WsEvent.AUTH_PERSON,
                    snakeKeys(mapOf("widgetId" to externalId, "id" to personId)),
                )
            }, 1000)
        }
    }

    private fun handleIncomingMessage(data: Message, unread: Boolean = true) {
        setOperatorTyping(false)
        val withDate = if (data.date == null) {
            data.copy(date = DateObject(date = Instant.now().toString(), timezone = "UTC"))
        } else data
        val next = withDate.copy(
            unread = unread && !opened,
            isOwn = false,
            emojiOnly = withDate.emojiOnly || isEmojiOnly((withDate.message ?: "").trim()),
            message = withDate.originalMessage ?: withDate.message,
        )
        messages = messages + next
        setMessages(messages)
        if (!next.appealId.isNullOrBlank() && next.appealId != appealIdValue) {
            setAppealId(next.appealId)
            emitter.emit("appealStarted", next)
        }
    }

    private fun handleReceived(data: JSONObject) {
        banned = data.optBool("banned")
        val tempId = data.optStr("temp_id") ?: data.optStr("tempId") ?: return
        val newId = data.optStr("id")
        val appealId = data.optStr("appeal_id") ?: data.optStr("appealId")
        receivedTimeouts.remove(tempId)?.let { mainHandler.removeCallbacks(it) }
        if (!appealId.isNullOrBlank() && appealIdValue != appealId) {
            setAppealId(appealId)
        }
        val idx = messages.indexOfFirst { !it.fromInvite && it.tempId == tempId }
        if (idx >= 0) {
            messages = messages.toMutableList().also {
                it[idx] = it[idx].copy(
                    id = newId,
                    tempId = newId ?: it[idx].tempId,
                    status = MessageStatus.Sent,
                    appealId = appealIdValue,
                    date = DateObject(date = Instant.now().toString(), timezone = "UTC"),
                )
            }
        }
        removePending(tempId)
        setMessages(messages)
    }

    private fun handleSeen(data: JSONObject) {
        val ids = data.optArr("ids")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
        val appeal = data.optStr("appeal_id")
        messages = messages.map { m ->
            val byId = m.id in ids || m.tempId in ids
            val byAppeal = ids.isEmpty() && !appeal.isNullOrBlank() && m.appealId == appeal
            if (byId || byAppeal) m.copy(seen = true, unread = false) else m
        }
        setMessages(messages)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val message = Message(
            message = trimmed,
            isOwn = true,
            date = DateObject(date = Instant.now().toString(), timezone = "UTC"),
            emojiOnly = isEmojiOnly(trimmed),
            messageType = MessageType.Text,
            tempId = guid(),
        )
        handleSendMessage(message)
    }

    fun sendAttachment(attachment: UploadedAttachment) {
        val message = Message(
            isOwn = true,
            date = DateObject(date = Instant.now().toString(), timezone = "UTC"),
            attachment = attachment.attachment,
            attachmentId = attachment.attachmentId,
            attachmentUrl = attachment.attachmentUrl,
            width = attachment.width,
            height = attachment.height,
            size = attachment.size,
            messageType = attachment.type,
            tempId = guid(),
        )
        handleSendMessage(message, save = false)
    }

    suspend fun uploadAttachment(file: LocalFile, onProgress: UploadProgressHandler? = null): UploadedAttachment {
        if (file.size != null && file.size > MAX_UPLOAD_BYTES) {
            val error = IllegalStateException("fileSizeError")
            emitter.emit("error", error)
            throw error
        }
        if (!isAllowedUploadFile(file)) {
            val error = IllegalStateException("fileFormatNotAllowed")
            emitter.emit("error", error)
            throw error
        }
        val res = try {
            withContext(Dispatchers.IO) { api.uploadFile(file, onProgress) }
        } catch (e: Throwable) {
            val error = if (e is IllegalStateException) e else IllegalStateException("uploadFailed", e)
            emitter.emit("error", error)
            throw error
        }
        if (res.optBool("success", true).not() && res.has("success")) {
            val detail = res.optArr("error")?.let { arr ->
                (0 until arr.length()).joinToString(", ") { arr.optString(it) }
            } ?: res.optStr("error") ?: "uploadFailed"
            val error = IllegalStateException(detail.ifBlank { "uploadFailed" })
            emitter.emit("error", error)
            throw error
        }
        val data = res.opt("data") as? JSONObject ?: JSONObject()
        val attachmentId = data.optStr("attachmentId") ?: data.optStr("attachment_id") ?: ""
        val attachmentUrl = data.optStr("attachment") ?: data.optStr("attachmentUrl") ?: file.uri
        return UploadedAttachment(
            attachment = file.name,
            attachmentId = attachmentId,
            attachmentUrl = attachmentUrl,
            type = messageTypeFromFile(file),
            size = file.size,
            width = file.width,
            height = file.height,
        )
    }

    suspend fun uploadFile(file: LocalFile) {
        if (file.size != null && file.size > MAX_UPLOAD_BYTES) {
            addAttachmentError(file, "fileSizeError")
            val error = IllegalStateException("fileSizeError")
            emitter.emit("error", error)
            throw error
        }
        if (!isAllowedUploadFile(file)) {
            addAttachmentError(file, "fileFormatNotAllowed")
            val error = IllegalStateException("fileFormatNotAllowed")
            emitter.emit("error", error)
            throw error
        }
        val localId = "${file.name}_${file.size ?: 0}_${System.currentTimeMillis()}_${guid().take(8)}"
        val kind = messageTypeFromFile(file)
        val preview = Message(
            message = "",
            messageType = kind,
            progress = 0,
            attachmentId = localId,
            tempId = guid(),
            isOwn = true,
            attachment = file.name,
            attachmentPreviewUrl = file.previewUri ?: if (kind == MessageType.Image) file.uri else null,
            attachmentUrl = file.uri,
            date = DateObject(date = Instant.now().toString(), timezone = "UTC"),
            size = file.size,
            width = file.width,
            height = file.height,
            status = MessageStatus.Sending,
        )
        messages = messages + preview
        setMessages(messages)
        try {
            val uploaded = uploadAttachment(file) { pct ->
                runOnMain { patchAttachment(localId, progress = pct) }
            }
            patchAttachment(
                localId,
                progress = 100,
                attachmentUrl = uploaded.attachmentUrl,
                newAttachmentId = uploaded.attachmentId,
            )
            sendAttachment(uploaded)
        } catch (e: Throwable) {
            val code = e.message ?: "uploadFailed"
            patchAttachment(localId, errorMessage = code, progress = 100, status = MessageStatus.Failed)
            throw e
        }
    }

    private fun addAttachmentError(file: LocalFile, errorMessage: String) {
        val message = Message(
            message = "",
            messageType = messageTypeFromFile(file),
            progress = 100,
            attachmentId = "${file.name}${file.size ?: 0}",
            tempId = guid(),
            isOwn = true,
            attachment = file.name,
            attachmentPreviewUrl = file.uri,
            date = DateObject(date = Instant.now().toString(), timezone = "UTC"),
            size = file.size,
            errorMessage = errorMessage,
            status = MessageStatus.Failed,
        )
        messages = messages + message
        setMessages(messages)
    }

    private fun patchAttachment(
        attachmentId: String,
        progress: Int? = null,
        attachmentUrl: String? = null,
        errorMessage: String? = null,
        status: MessageStatus? = null,
        newAttachmentId: String? = null,
    ) {
        messages = messages.map { m ->
            if (m.attachmentId != attachmentId) m
            else m.copy(
                progress = progress ?: m.progress,
                attachmentUrl = attachmentUrl ?: m.attachmentUrl,
                errorMessage = errorMessage ?: m.errorMessage,
                status = status ?: m.status,
                attachmentId = newAttachmentId ?: m.attachmentId,
            )
        }
        setMessages(messages)
    }

    private fun handleSendMessage(message: Message, save: Boolean = true) {
        ensureConnected()
        if (!messageWasSent) {
            messageWasSent = true
            scope.launch { sendPersonData() }
        }
        var outgoing = message.copy(
            status = if (banned) MessageStatus.Sent else MessageStatus.Sending,
            tempId = guid(),
        )
        val userMessages = messages.filter {
            it.isOwn && it.errorMessage == null && it.attachment == null &&
                it.status != MessageStatus.Failed && it.tempId != outgoing.tempId
        }
        val attachmentMessages = messages.filter {
            it.isOwn && it.errorMessage == null && it.attachment != null &&
                it.status != MessageStatus.Failed && it.tempId != outgoing.tempId
        }
        val last = messages.lastOrNull()
        val hasRating = last?.formType == FormType.Rating
        val isFirstMessage = appealIdValue.isBlank() && (
            (userMessages.isEmpty() && attachmentMessages.isEmpty()) ||
                hasRating ||
                (outgoing.attachment != null && attachmentMessages.isNotEmpty() &&
                    outgoing.attachment == attachmentMessages.first().attachment)
            )

        if (outgoing.messageType != null && outgoing.messageType != MessageType.Text && !outgoing.attachmentId.isNullOrBlank()) {
            messages = messages.map { m ->
                if (m.attachmentId == outgoing.attachmentId) m.copy(tempId = outgoing.tempId) else m
            }
        }
        if (save) {
            messages = messages + outgoing
        }
        setMessages(messages)
        pushPending(outgoing)
        if (isFirstMessage || appealIdValue.isNotBlank()) {
            emitMessage(outgoing)
        } else {
            messagesInPending += outgoing
        }
        if (isFirstMessage) emitter.emit("dialogStarted", outgoing)
        emitter.emit("messageSent", outgoing)
    }

    private fun emitMessage(message: Message) {
        ensureConnected()
        val pid = personId.ifBlank { storage.getString(StorageKeys.PERSON_ID) }
        val payload = snakeKeys(
            mapOf(
                "external_id" to externalId,
                "message" to message.toJson(),
                "person_id" to pid,
                "appeal_id" to appealIdValue,
            ),
        )
        socket.emit(WsEvent.MESSAGE_FROM_PERSON, payload)
        armReceivedTimeout(message)
    }

    private fun armReceivedTimeout(message: Message) {
        if (banned || message.tempId.isBlank()) return
        receivedTimeouts.remove(message.tempId)?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    api.reportError(
                        mapOf(
                            "error" to "Did not receive \"received\" event from websockets",
                            "widgetId" to externalId,
                            "appealId" to appealIdValue,
                            "personId" to personId,
                        ),
                    )
                }
            }
            messages = messages.map {
                if (it.tempId == message.tempId) it.copy(status = MessageStatus.Failed) else it
            }
            setMessages(messages)
        }
        receivedTimeouts[message.tempId] = runnable
        mainHandler.postDelayed(runnable, RECEIVED_ACK_TIMEOUT_MS)
    }

    fun retryMessage(tempId: String) {
        val message = messages.find { it.tempId == tempId } ?: return
        val next = message.copy(status = MessageStatus.Sending, errorMessage = null)
        messages = messages.map { if (it.tempId == tempId) next else it }
        setMessages(messages)
        handleSendMessage(next, save = false)
    }

    fun setPersonData(info: AdditionalPersonInfo) {
        personAdditionalInfo = personAdditionalInfo.copy(
            name = info.name ?: personAdditionalInfo.name,
            email = info.email ?: personAdditionalInfo.email,
            avatar = info.avatar ?: personAdditionalInfo.avatar,
            phone = info.phone ?: personAdditionalInfo.phone,
            payload = personAdditionalInfo.payload + info.payload,
        )
        if (personId.isNotBlank()) {
            scope.launch { sendPersonData() }
        }
    }

    fun greetingMessages(messages: List<String>, fromBot: Boolean = true) {
        if (greetingSent) return
        greetingSent = true
        val operatorAvatar = if (!fromBot) operators.firstOrNull()?.avatar else null
        messages.forEach { text ->
            handleIncomingMessage(
                Message(
                    message = text,
                    tempId = guid(),
                    serviceMessage = true,
                    avatar = operatorAvatar,
                    from = operatorAvatar?.let { app.teletype.sdk.types.MessageFrom(avatar = it) },
                ),
                unread = false,
            )
        }
    }

    fun setMessage(text: String) {
        draft = text
        emitter.emit("draftChanged", text)
        publishState()
    }

    fun show() = setOpened(true)
    fun hide() = setOpened(false)
    fun toggle() = setOpened(!opened)

    private fun setOpened(value: Boolean) {
        if (opened == value) return
        opened = value
        storage.set(StorageKeys.POPUP_STATE, value)
        emitter.emit("openChanged", value)
        if (value) {
            messages = messages.map { m ->
                if (!m.isOwn && m.unread) m.copy(unread = false, seen = true) else m
            }
            setMessages(messages)
            ensureConnected()
            storage.set(StorageKeys.WIDGET_INTERACTED, true)
            scope.launch {
                val ip = runCatching { withContext(Dispatchers.IO) { api.getIp().dataString() } }.getOrNull()
                socket.emit(
                    WsEvent.PERSON_OPENED_WIDGET,
                    snakeKeys(
                        mapOf(
                            "channel_id" to externalId,
                            "ipAddress" to ip,
                            "personId" to personId.ifBlank { null },
                            "appealId" to appealIdValue.ifBlank { null },
                            "data" to emptyMap<String, Any>(),
                        ),
                    ),
                )
            }
            markSeen()
            emitter.emit("opened")
        } else {
            emitter.emit("closed")
        }
        publishState()
    }

    private suspend fun sendPersonData() {
        val ip = runCatching { withContext(Dispatchers.IO) { api.getIp().dataString() } }.getOrNull()
        val info = device.toMutableMap<String, Any?>()
        info["ipAddress"] = ip
        personAdditionalInfo.name?.let { info["name"] = it }
        personAdditionalInfo.email?.let { info["email"] = it }
        personAdditionalInfo.avatar?.let { info["avatar"] = it }
        personAdditionalInfo.phone?.let { info["phone"] = it }
        if (personAdditionalInfo.payload.isNotEmpty()) info["payload"] = personAdditionalInfo.payload
        socket.emit(
            WsEvent.SET_PERSON_DATA,
            snakeKeys(
                mapOf(
                    "widgetId" to externalId,
                    "personId" to personId,
                    "data" to info,
                ),
            ),
        )
    }

    private fun setUserData(data: Map<String, Any?>) {
        if (personId.isNotBlank()) {
            socket.emit(WsEvent.SET_USER_DATA, snakeKeys(mapOf("personId" to personId, "data" to data)))
        } else {
            personData = data
        }
    }

    fun rateAppeal(value: ConversationRating, formTempId: String? = null) {
        socket.emit(
            WsEvent.PERSON_RATE_APPEAL,
            snakeKeys(mapOf("appealId" to appealIdValue, "data" to mapOf("value" to value.wire))),
        )
        if (!formTempId.isNullOrBlank()) {
            val idx = messages.indexOfFirst { it.tempId == formTempId }
            if (idx >= 0) {
                messages = messages.toMutableList().also {
                    it[idx] = it[idx].copy(ratingValue = value)
                }
                setMessages(messages)
            }
        }
        emitter.emit("dialogRated", mapOf("id" to (formTempId ?: ""), "formValue" to value.wire))
        publishState()
    }

    fun clickChannel(channel: ChannelMessenger) {
        emitter.emit("channelClicked", channel)
        scope.launch {
            val ip = runCatching { withContext(Dispatchers.IO) { api.getIp().dataString() } }.getOrNull()
            socket.emit(
                WsEvent.PERSON_CLICK_WIDGET_CHANNEL,
                snakeKeys(
                    mapOf(
                        "widgetId" to externalId,
                        "personId" to personId.ifBlank { null },
                        "appealId" to appealIdValue.ifBlank { null },
                        "widgetChannelId" to channel.id,
                        "data" to mapOf("value" to channel.name, "ipAddress" to ip),
                    ),
                ),
            )
        }
    }

    fun markSeen() {
        if (appealIdValue.isNotBlank()) {
            socket.emit(WsEvent.SEEN, snakeKeys(mapOf("appealId" to appealIdValue)))
        }
    }

    fun startTyping(message: String = "") {
        if (socket.connected) {
            socket.emit(WsEvent.PERSON_TYPING, JSONObject().put("appealId", appealIdValue).put("message", message))
        }
    }

    fun stopTyping() {
        if (socket.connected) {
            socket.emit(WsEvent.PERSON_STOP_TYPING, JSONObject().put("appealId", appealIdValue))
        }
    }

    fun notifyLocationChange(location: String) {
        scope.launch(Dispatchers.IO) { runCatching { api.notifyLocationChange(location) } }
    }

    private fun createRatingForm() {
        handleIncomingMessage(
            Message(tempId = guid(), formType = FormType.Rating, serviceMessage = true),
            unread = false,
        )
    }

    private fun setAppealId(appealId: String) {
        appealIdValue = appealId
        storage.set(StorageKeys.APPEAL_ID, appealId)
    }

    private fun setOperatorTyping(value: Boolean) {
        if (operatorTyping != value) {
            operatorTyping = value
            emitter.emit("operatorTyping", value)
            publishState()
        }
    }

    private fun setMessages(next: List<Message>, fromHistory: Boolean = false) {
        val sent = uniqueBy(next.filter { (it.id != null || !it.isOwn) && !it.serviceMessage }) { it.id ?: it.tempId }
        val pending = uniqueBy(next.filter { it.id == null && it.formType != FormType.ContactButtons }) { it.tempId }
        val merged = (sent + pending).sortedBy { it.date?.date?.let { d -> runCatching { Instant.parse(d).toEpochMilli() }.getOrDefault(0L) } ?: 0L }
        var unread = 0
        merged.forEach { if (!it.isOwn && it.unread) unread++ }
        messages = merged
        setUnreadCount(unread)
        publishMessages()
        publishState()
    }

    private fun setUnreadCount(count: Int) {
        if (unreadCount != count) {
            unreadCount = count
            emitter.emit("unreadCountChanged", count)
        }
    }

    private fun publishMessages() {
        emitter.emit("messagesChanged", messages)
    }

    private fun pushPending(message: Message) {
        pendingMessages += message
        savePending()
    }

    private fun removePending(tempId: String) {
        val idx = pendingMessages.indexOfFirst { !it.fromInvite && it.tempId == tempId }
        if (idx >= 0) {
            pendingMessages.removeAt(idx)
            savePending()
        }
    }

    private fun savePending() {
        val arr = JSONArray()
        pendingMessages.filter { it.messageType == MessageType.Text }.forEach { arr.put(it.toJson()) }
        storage.set(StorageKeys.PENDING_MESSAGES, arr)
    }

    fun destroy() {
        receivedTimeouts.values.forEach { mainHandler.removeCallbacks(it) }
        receivedTimeouts.clear()
        socket.off()
        socket.disconnect()
        emitter.removeAllListeners()
        scope.cancel()
        initialized = false
        listenersBound = false
    }

    private fun buildState(): TeletypeState = TeletypeState(
        ready = ready,
        opened = opened,
        connection = connection,
        messages = messages,
        operators = operators,
        channels = channels,
        config = widgetConfig,
        unreadCount = unreadCount,
        operatorTyping = operatorTyping,
        draft = draft,
        title = titleValue,
        primaryColor = primaryColor,
        chatBackground = chatBackgroundValue,
        locale = localeValue,
        strings = stringsValue,
    )

    private fun publishState() {
        _state.value = buildState()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }
}

private fun isUsableConfigResponse(response: JSONObject?): Boolean {
    if (response == null) return false
    if (response.has("success") && !response.optBool("success", true)) return false
    val data = response.opt("data")
    if (data == null || data is JSONArray || data !is JSONObject) return false
    return data.optStr("projectId")?.isNotBlank() == true || data.optStr("name")?.isNotBlank() == true
}
