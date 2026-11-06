package app.teletype.sdk.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.teletype.sdk.config.extractTeletypeExternalId
import app.teletype.sdk.config.isTeletypeExternalId
import app.teletype.sdk.config.looksLikeEmbedSnippet
import app.teletype.sdk.core.SharedPrefsStore
import app.teletype.sdk.core.TeletypeClient
import app.teletype.sdk.core.TeletypeConfig
import app.teletype.sdk.core.localFileFromUri
import app.teletype.sdk.config.TeletypeMode
import app.teletype.sdk.i18n.getSystemLanguage
import app.teletype.sdk.types.CHAT_BUTTON_SCALES
import app.teletype.sdk.types.ChatBackground
import app.teletype.sdk.types.ChatButtonPosition
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.ui.ChatBackgroundLayer
import app.teletype.sdk.ui.ChatScreen
import app.teletype.sdk.ui.ChatWidget
import app.teletype.sdk.ui.TeletypeProvider
import app.teletype.sdk.ui.parseColor
import app.teletype.sdk.ui.rememberTeletypeClient
import app.teletype.sdk.ui.rememberTeletypeState
import app.teletype.sdk.ui.rememberUnreadCount
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import kotlinx.coroutines.launch

private const val EXTERNAL_ID = ""
private const val STORAGE_KEY = "teletype_demo_external_id"
private const val STORAGE_KEY_TITLE = "teletype_demo_chat_title"
private const val STORAGE_KEY_COLOR = "teletype_demo_primary_color"
private const val STORAGE_KEY_BG = "teletype_demo_chat_background"
private const val STORAGE_KEY_BTN_POS = "teletype_demo_button_position"
private const val STORAGE_KEY_BTN_SCALE = "teletype_demo_button_scale"

private val CHAT_COLORS = listOf("#0084ff", "#34c759", "#ff3b30", "#7c3aed", "#ffcc00")
private val LOCALE_ORDER = listOf("en", "ru")

private enum class Session { Boot, Setup, Connecting, Ready }
private enum class SetupError { Format, Snippet, Channel, Connection }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DemoRoot() }
    }
}

@Composable
private fun DemoRoot() {
    val context = LocalContext.current
    val store = remember { SharedPrefsStore(context) }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf(Session.Boot) }
    var externalId by remember { mutableStateOf("") }
    var draftId by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<SetupError?>(null) }
    var uiLocale by remember {
        mutableStateOf(if (getSystemLanguage() == "ru") "ru" else "en")
    }
    var chatTitle by remember { mutableStateOf("") }
    var primaryColor by remember { mutableStateOf("#0084ff") }
    var chatBackground by remember { mutableStateOf(ChatBackground.White) }
    var buttonPosition by remember { mutableStateOf(ChatButtonPosition.Right) }
    var buttonScale by remember { mutableIntStateOf(100) }
    var channelClient by remember { mutableStateOf<TeletypeClient?>(null) }

    val resolvedTitle = chatTitle.trim().ifBlank { getDemoStrings(uiLocale).chatTitle }

    fun persist(key: String, value: String) {
        store.setItem(key, value)
    }

    fun dropClient() {
        channelClient?.destroy()
        channelClient = null
    }

    fun cycleLocale() {
        val next = LOCALE_ORDER[(LOCALE_ORDER.indexOf(uiLocale) + 1) % LOCALE_ORDER.size]
        uiLocale = next
        channelClient?.setLocale(next)
    }

    suspend fun verifyAndEnter(id: String) {
        setupError = null
        session = Session.Connecting
        dropClient()
        val client = TeletypeClient(
            context,
            TeletypeConfig(
                teletypeExternalId = id,
                mode = TeletypeMode.Dev,
                soundEnabled = true,
                locale = uiLocale,
                translations = demoTranslations,
                title = resolvedTitle,
                primaryColor = primaryColor,
                chatBackground = chatBackground,
            ),
        )
        try {
            client.init()
            if (client.state.config == null) throw IllegalStateException("channel")
            client.waitUntilConnected(15_000)
            channelClient = client
            persist(STORAGE_KEY, id)
            externalId = id
            session = Session.Ready
        } catch (err: Throwable) {
            client.destroy()
            setupError = if (err.message == "channel") SetupError.Channel else SetupError.Connection
            draftId = id
            externalId = ""
            session = Session.Setup
        }
    }

    LaunchedEffect(Unit) {
        var storedId = EXTERNAL_ID
        val stored = store.getItem(STORAGE_KEY)
        val storedTitle = store.getItem(STORAGE_KEY_TITLE)
        val storedColor = store.getItem(STORAGE_KEY_COLOR)
        val storedBg = store.getItem(STORAGE_KEY_BG)
        val storedPos = store.getItem(STORAGE_KEY_BTN_POS)
        val storedScale = store.getItem(STORAGE_KEY_BTN_SCALE)
        if (storedId.isBlank()) storedId = stored.orEmpty()
        if (!storedTitle.isNullOrBlank()) chatTitle = storedTitle
        if (!storedColor.isNullOrBlank()) primaryColor = storedColor
        ChatBackground.fromId(storedBg)?.let { chatBackground = it }
        if (storedPos == "left") buttonPosition = ChatButtonPosition.Left
        if (storedPos == "right") buttonPosition = ChatButtonPosition.Right
        storedScale?.toIntOrNull()?.takeIf { it in CHAT_BUTTON_SCALES }?.let { buttonScale = it }

        val parsed = extractTeletypeExternalId(storedId)
        if (parsed.isBlank()) {
            draftId = storedId
            session = Session.Setup
            return@LaunchedEffect
        }
        if (!isTeletypeExternalId(parsed)) {
            draftId = storedId
            setupError = SetupError.Format
            session = Session.Setup
            return@LaunchedEffect
        }
        draftId = parsed
        verifyAndEnter(parsed)
    }

    when {
        session == Session.Boot || session == Session.Connecting -> ConnectingScreen(uiLocale)
        session != Session.Ready || channelClient == null -> SetupScreen(
            locale = uiLocale,
            value = draftId,
            error = setupError,
            onChangeValue = {
                draftId = it
                setupError = null
            },
            onCycleLocale = { cycleLocale() },
            onConnect = { raw ->
                val parsed = extractTeletypeExternalId(raw)
                if (parsed.isBlank()) {
                    setupError = if (looksLikeEmbedSnippet(raw)) SetupError.Snippet else SetupError.Format
                    return@SetupScreen
                }
                if (!isTeletypeExternalId(parsed)) {
                    setupError = SetupError.Format
                    return@SetupScreen
                }
                draftId = parsed
                scope.launch { verifyAndEnter(parsed) }
            },
        )
        else -> TeletypeProvider(client = channelClient!!) {
            DemoHome(
                locale = uiLocale,
                onCycleLocale = { cycleLocale() },
                onClearCache = {
                    scope.launch {
                        store.getAllKeys()
                            .filter { it.startsWith("teletype-") || it == STORAGE_KEY }
                            .forEach { store.removeItem(it) }
                        dropClient()
                        externalId = ""
                        draftId = ""
                        setupError = null
                        session = Session.Setup
                    }
                },
                chatTitle = resolvedTitle,
                onChangeTitle = {
                    chatTitle = it
                    persist(STORAGE_KEY_TITLE, it)
                    channelClient?.setTitle(it.ifBlank { getDemoStrings(uiLocale).chatTitle })
                },
                primaryColor = primaryColor,
                onChangeColor = {
                    primaryColor = it
                    persist(STORAGE_KEY_COLOR, it)
                    channelClient?.setPrimaryColor(it)
                },
                chatBackground = chatBackground,
                onChangeBackground = {
                    chatBackground = it
                    persist(STORAGE_KEY_BG, it.id)
                    channelClient?.setChatBackground(it)
                },
                buttonPosition = buttonPosition,
                onChangePosition = {
                    buttonPosition = it
                    persist(STORAGE_KEY_BTN_POS, if (it == ChatButtonPosition.Left) "left" else "right")
                },
                buttonScale = buttonScale,
                onChangeScale = {
                    buttonScale = it
                    persist(STORAGE_KEY_BTN_SCALE, it.toString())
                },
            )
        }
    }
}

@Composable
private fun DemoHome(
    locale: String,
    onCycleLocale: () -> Unit,
    onClearCache: () -> Unit,
    chatTitle: String,
    onChangeTitle: (String) -> Unit,
    primaryColor: String,
    onChangeColor: (String) -> Unit,
    chatBackground: ChatBackground,
    onChangeBackground: (ChatBackground) -> Unit,
    buttonPosition: ChatButtonPosition,
    onChangePosition: (ChatButtonPosition) -> Unit,
    buttonScale: Int,
    onChangeScale: (Int) -> Unit,
) {
    val client = rememberTeletypeClient()
    val state = rememberTeletypeState()
    val unread = rememberUnreadCount()
    val t = getDemoStrings(locale)
    val context = LocalContext.current
    var pickWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<LocalFile?>?>(null) }
    val filePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        val waiter = pickWaiter
        pickWaiter = null
        if (uri == null) {
            waiter?.complete(null)
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        waiter?.complete(runCatching { localFileFromUri(context, uri) }.getOrNull())
    }

    if (state.opened) {
        ChatScreen(
            onClose = { client.hide() },
            onAttachmentPress = {
                val waiter = kotlinx.coroutines.CompletableDeferred<LocalFile?>()
                pickWaiter = waiter
                filePicker.launch(arrayOf("*/*"))
                waiter.await()
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(top = 36.dp, bottom = 128.dp),
        ) {
            Text(t.screenTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111))
            Text(
                t.screenSubtitle.replace("{unread}", unread.toString()),
                fontSize = 15.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            FieldLabel(t.chatTitleLabel)
            DemoInput(chatTitle, t.chatTitlePlaceholder, onChangeTitle)

            FieldLabel(t.chatColorLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                CHAT_COLORS.forEach { color ->
                    val selected = color.equals(primaryColor, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(parseColor(color) ?: Color.Blue)
                            .clickable { onChangeColor(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            FieldLabel(t.chatBackgroundLabel)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                ChatBackground.PICKER.forEach { id ->
                    val selected = id == chatBackground
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.5.dp,
                                if (selected) Color(0xFF0084FF) else Color(0xFFD0D5DA),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onChangeBackground(id) },
                    ) {
                        ChatBackgroundLayer(id)
                    }
                }
            }

            FieldLabel(t.buttonPositionLabel)
            DemoSelect(
                title = t.buttonPositionLabel,
                value = if (buttonPosition == ChatButtonPosition.Right) t.buttonPositionRight else t.buttonPositionLeft,
                options = listOf(t.buttonPositionRight to ChatButtonPosition.Right, t.buttonPositionLeft to ChatButtonPosition.Left),
                onChange = { onChangePosition(it) },
            )

            FieldLabel(t.buttonScaleLabel)
            DemoSelect(
                title = t.buttonScaleLabel,
                value = "$buttonScale%",
                options = CHAT_BUTTON_SCALES.reversed().map { "$it%" to it },
                onChange = { onChangeScale(it) },
            )

            Spacer(Modifier.height(8.dp))
            DemoButton(t.openChat, Color(0xFF0084FF)) { client.show() }
            DemoButton(t.clearCache, Color(0xFFE5352B), onClick = onClearCache)
            OutlinedButton(
                onClick = onCycleLocale,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(t.languageButton.replace("{lang}", locale.uppercase()), color = Color(0xFF0084FF), fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            Modifier.align(
                if (buttonPosition == ChatButtonPosition.Left) Alignment.BottomStart
                else Alignment.BottomEnd,
            ),
        ) {
            ChatWidget(position = buttonPosition, scale = buttonScale, bottom = 40)
        }
    }
}

@Composable
private fun <T> DemoSelect(
    title: String,
    value: String,
    options: List<Pair<String, T>>,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .height(48.dp)
            .border(1.dp, Color(0xFFD0D5DA), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(value, fontSize = 15.sp, color = Color(0xFF111111), modifier = Modifier.weight(1f))
            Text("▼", fontSize = 10.sp, color = Color(0xFF111111))
        }
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val dialogView = LocalView.current
            SideEffect {
                (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { open = false },
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                    options.forEach { (label, item) ->
                        val active = label == value
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (active) Color(0xFFF3F4F6) else Color.Transparent)
                                .clickable {
                                    onChange(item)
                                    open = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            fontSize = 16.sp,
                            color = Color(0xFF111111),
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    locale: String,
    value: String,
    error: SetupError?,
    onChangeValue: (String) -> Unit,
    onCycleLocale: () -> Unit,
    onConnect: (String) -> Unit,
) {
    val t = getDemoStrings(locale)
    val parsedId = extractTeletypeExternalId(value)
    val idLooksValid = isTeletypeExternalId(parsedId)
    val canSubmit = value.trim().isNotEmpty()
    val errorText = when (error) {
        SetupError.Snippet -> t.snippetMissingId
        SetupError.Channel -> t.invalidChannel
        SetupError.Connection -> t.connectionFailed
        SetupError.Format -> t.invalidExternalId
        null -> ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(top = 36.dp),
    ) {
        Text(t.screenTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(t.setupTitle, fontSize = 15.sp, color = Color(0xFF666666), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        Text(t.setupHint, fontSize = 14.sp, color = Color(0xFF888888), modifier = Modifier.padding(bottom = 20.dp))
        OutlinedButton(
            onClick = onCycleLocale,
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(t.languageButton.replace("{lang}", locale.uppercase()), color = Color(0xFF0084FF))
        }
        DemoInput(value, t.externalIdPlaceholder, onChangeValue, singleLine = false, error = error != null)
        if (idLooksValid) {
            Text(t.parsedId.replace("{id}", parsedId), fontSize = 13.sp, color = Color(0xFF5F6368), modifier = Modifier.padding(bottom = 12.dp))
        }
        if (errorText.isNotBlank()) {
            Text(errorText, color = Color(0xFFE5352B), fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
        DemoButton(t.connect, Color(0xFF0084FF), enabled = canSubmit) { onConnect(value) }
    }
}

@Composable
private fun ConnectingScreen(locale: String) {
    val t = getDemoStrings(locale)
    Column(
        Modifier.fillMaxSize().background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Color(0xFF0084FF))
        Text(t.connecting, color = Color(0xFF666666), modifier = Modifier.padding(top = 16.dp), fontSize = 16.sp)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111), modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DemoInput(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    error: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (singleLine) 48.dp else 125.dp)
            .border(1.dp, if (error) Color(0xFFE5352B) else Color(0xFFD0D5DA), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .padding(bottom = if (singleLine) 16.dp else 0.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Color(0xFF9AA0A6), fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 15.sp),
            modifier = Modifier.fillMaxSize(),
            singleLine = singleLine,
        )
    }
    if (singleLine) Spacer(Modifier.height(16.dp))
}

@Composable
private fun DemoButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

private val demoTranslations: Map<String, Map<String, String>> = mapOf(
    "ru" to mapOf("statusOffline" to "Мы онлайн — ответим сразу"),
)
