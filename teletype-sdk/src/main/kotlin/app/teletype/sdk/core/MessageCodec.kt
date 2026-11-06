package app.teletype.sdk.core

import app.teletype.sdk.json.optArr
import app.teletype.sdk.json.optBool
import app.teletype.sdk.json.optIntOrNull
import app.teletype.sdk.json.optLongOrNull
import app.teletype.sdk.json.optObj
import app.teletype.sdk.json.optStr
import app.teletype.sdk.types.ChannelMessenger
import app.teletype.sdk.types.ConversationRating
import app.teletype.sdk.types.DateObject
import app.teletype.sdk.types.FormType
import app.teletype.sdk.types.Message
import app.teletype.sdk.types.MessageFrom
import app.teletype.sdk.types.MessageStatus
import app.teletype.sdk.types.MessageType
import app.teletype.sdk.types.Operator
import app.teletype.sdk.types.WidgetConfigData
import org.json.JSONArray
import org.json.JSONObject

fun Message.toJson(): JSONObject = JSONObject().also { o ->
    putOpt(o, "id", id)
    o.put("tempId", tempId)
    putOpt(o, "appealId", appealId)
    putOpt(o, "message", message)
    putOpt(o, "originalMessage", originalMessage)
    putOpt(o, "attachment", attachment)
    putOpt(o, "attachmentUrl", attachmentUrl)
    putOpt(o, "attachmentPreviewUrl", attachmentPreviewUrl)
    putOpt(o, "attachmentId", attachmentId)
    o.put("isOwn", isOwn)
    date?.let { o.put("date", JSONObject().put("date", it.date).put("timezone", it.timezone)) }
    putOpt(o, "dateToDisplay", dateToDisplay)
    putOpt(o, "avatar", avatar)
    putOpt(o, "avatarDefault", avatarDefault)
    putOpt(o, "messageType", messageType?.wire)
    o.put("emojiOnly", emojiOnly)
    progress?.let { o.put("progress", it) }
    o.put("isForm", isForm)
    putOpt(o, "formId", formId)
    putOpt(o, "formType", formType?.wire)
    putOpt(o, "ratingValue", ratingValue?.wire)
    from?.let { o.put("from", fromToJson(it)) }
    o.put("fromInvite", fromInvite)
    o.put("serviceMessage", serviceMessage)
    putOpt(o, "errorMessage", errorMessage)
    width?.let { o.put("width", it) }
    height?.let { o.put("height", it) }
    o.put("unread", unread)
    o.put("seen", seen)
    putOpt(o, "status", status?.wire)
    size?.let { o.put("size", it) }
}

fun messageFromJson(raw: JSONObject): Message {
    val dateObj = raw.optObj("date")
    return Message(
        id = raw.optStr("id"),
        tempId = raw.optStr("tempId") ?: guid(),
        appealId = raw.optStr("appealId"),
        message = raw.optStr("message"),
        originalMessage = raw.optStr("originalMessage"),
        attachment = raw.optStr("attachment"),
        attachmentUrl = raw.optStr("attachmentUrl"),
        attachmentPreviewUrl = raw.optStr("attachmentPreviewUrl"),
        attachmentId = raw.optStr("attachmentId"),
        isOwn = raw.optBool("isOwn"),
        date = dateObj?.let {
            DateObject(
                date = it.optStr("date") ?: "",
                timezone = it.optStr("timezone") ?: "UTC",
            )
        },
        dateToDisplay = raw.optStr("dateToDisplay"),
        avatar = raw.optStr("avatar"),
        avatarDefault = raw.optStr("avatarDefault"),
        messageType = MessageType.fromWire(raw.optStr("messageType")),
        emojiOnly = raw.optBool("emojiOnly"),
        progress = raw.optIntOrNull("progress"),
        isForm = raw.optBool("isForm"),
        formId = raw.optStr("formId"),
        formType = FormType.fromWire(raw.optStr("formType")),
        ratingValue = ConversationRating.fromWire(raw.optStr("ratingValue")),
        from = raw.optObj("from")?.let { fromFromJson(it) },
        operator = raw.optObj("operator")?.let { operatorFromJson(it) },
        fromInvite = raw.optBool("fromInvite"),
        serviceMessage = raw.optBool("serviceMessage"),
        errorMessage = raw.optStr("errorMessage"),
        width = raw.optIntOrNull("width"),
        height = raw.optIntOrNull("height"),
        unread = raw.optBool("unread"),
        seen = raw.optBool("seen"),
        status = MessageStatus.fromWire(raw.optStr("status")),
        size = raw.optLongOrNull("size"),
    )
}

fun operatorFromJson(raw: JSONObject): Operator = Operator(
    name = raw.optStr("name") ?: "",
    isOnline = raw.optBool("isOnline"),
    avatar = raw.optStr("avatar") ?: "",
    id = raw.optStr("id") ?: raw.optStr("clientId"),
    messenger = raw.optStr("messenger"),
    avatarDefault = raw.optStr("avatarDefault"),
)

fun fromFromJson(raw: JSONObject): MessageFrom = MessageFrom(
    avatar = raw.optStr("avatar"),
    avatarDefault = raw.optStr("avatarDefault"),
    id = raw.optStr("id"),
    isOnline = if (raw.has("isOnline")) raw.optBool("isOnline") else null,
    isOperator = if (raw.has("isOperator")) raw.optBool("isOperator") else null,
    messenger = raw.optStr("messenger"),
    name = raw.optStr("name"),
)

fun fromToJson(from: MessageFrom): JSONObject = JSONObject().also { o ->
    putOpt(o, "avatar", from.avatar)
    putOpt(o, "avatarDefault", from.avatarDefault)
    putOpt(o, "id", from.id)
    from.isOnline?.let { o.put("isOnline", it) }
    from.isOperator?.let { o.put("isOperator", it) }
    putOpt(o, "messenger", from.messenger)
    putOpt(o, "name", from.name)
}

fun channelFromJson(raw: JSONObject): ChannelMessenger = ChannelMessenger(
    id = raw.optStr("id"),
    name = raw.optStr("name") ?: "",
    desktopUrl = raw.optStr("desktopUrl"),
    mobileUrl = raw.optStr("mobileUrl"),
)

fun configFromJson(raw: JSONObject): WidgetConfigData {
    val operators = raw.optArr("operators")?.toObjList()?.map { operatorFromJson(it) }.orEmpty()
    val channels = raw.optArr("channels")?.toObjList()?.map { channelFromJson(it) }.orEmpty()
    return WidgetConfigData(
        name = raw.optStr("name") ?: "",
        projectName = raw.optStr("projectName") ?: "",
        projectId = raw.optStr("projectId") ?: "",
        language = raw.optStr("language"),
        backgroundColor = raw.optStr("backgroundColor"),
        backgroundImage = raw.optStr("backgroundImage"),
        position = raw.optStr("position"),
        enabledChat = if (raw.has("enabledChat")) raw.optBool("enabledChat", true) else true,
        soundEnabled = if (raw.has("soundEnabled")) raw.optBool("soundEnabled") else null,
        operators = operators,
        channels = channels,
        timezone = raw.optStr("timezone"),
        showLink = raw.optBool("showLink"),
        buttonTextEnabled = if (raw.has("buttonTextEnabled")) raw.optBool("buttonTextEnabled") else null,
    )
}

fun messagesFromArray(arr: JSONArray?): List<Message> =
    arr?.toObjList()?.map { messageFromJson(it) }.orEmpty()

private fun putOpt(obj: JSONObject, key: String, value: String?) {
    if (value != null) obj.put(key, value)
}
