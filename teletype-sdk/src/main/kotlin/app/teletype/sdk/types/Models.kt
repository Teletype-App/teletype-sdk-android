package app.teletype.sdk.types

data class DateObject(
    val date: String = "",
    val timezone: String = "UTC",
)

data class BotButton(
    val label: String = "",
    val value: String = "",
)

data class ContactForm(
    val method: String = "phone",
    val value: String? = null,
    val valid: Boolean? = null,
    val placeholder: String? = null,
)

data class MessageFrom(
    val avatar: String? = null,
    val avatarDefault: String? = null,
    val id: String? = null,
    val isOnline: Boolean? = null,
    val isOperator: Boolean? = null,
    val messenger: String? = null,
    val name: String? = null,
)

data class Operator(
    val name: String = "",
    val isOnline: Boolean = false,
    val avatar: String = "",
    val id: String? = null,
    val messenger: String? = null,
    val avatarDefault: String? = null,
)

enum class MessageType(val wire: String) {
    Text("text"),
    Typing("typing"),
    Attachment("attachment"),
    Image("image"),
    Gif("gif"),
    Audio("audio"),
    Video("video");

    companion object {
        fun fromWire(value: String?): MessageType? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

enum class MessageStatus(val wire: String) {
    Sending("sending"),
    Sent("sent"),
    Failed("failed");

    companion object {
        fun fromWire(value: String?): MessageStatus? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

enum class FormType(val wire: String) {
    Rating("rating"),
    Boolean("boolean"),
    Faq("faq"),
    Input("input"),
    Contact("contact"),
    ContactButtons("contactButtons");

    companion object {
        fun fromWire(value: String?): FormType? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

enum class ConversationRating(val wire: String) {
    Unsatisfactory("10"),
    Bad("20"),
    Satisfactory("30"),
    Good("40"),
    Excellent("50");

    companion object {
        fun fromWire(value: String?): ConversationRating? =
            entries.firstOrNull { it.wire == value || it.name.equals(value, ignoreCase = true) }
    }
}

data class Message(
    val id: String? = null,
    val tempId: String = "",
    val appealId: String? = null,
    val message: String? = null,
    val originalMessage: String? = null,
    val attachment: String? = null,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentId: String? = null,
    val isOwn: Boolean = false,
    val date: DateObject? = null,
    val dateToDisplay: String? = null,
    val avatar: String? = null,
    val avatarDefault: String? = null,
    val messageType: MessageType? = null,
    val emojiOnly: Boolean = false,
    val progress: Int? = null,
    val isForm: Boolean = false,
    val formId: String? = null,
    val formType: FormType? = null,
    val ratingValue: ConversationRating? = null,
    val contactForm: ContactForm? = null,
    val operator: Operator? = null,
    val from: MessageFrom? = null,
    val fromInvite: Boolean = false,
    val serviceMessage: Boolean = false,
    val errorMessage: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val unread: Boolean = false,
    val seen: Boolean = false,
    val status: MessageStatus? = null,
    val size: Long? = null,
)

data class LocalFile(
    val uri: String,
    val name: String,
    val type: String,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val previewUri: String? = null,
)

data class UploadedAttachment(
    val attachment: String,
    val attachmentId: String,
    val attachmentUrl: String,
    val type: MessageType,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
)

data class AdditionalPersonInfo(
    val name: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val payload: Map<String, String> = emptyMap(),
)

data class ChannelMessenger(
    val id: String? = null,
    val name: String = "",
    val desktopUrl: String? = null,
    val mobileUrl: String? = null,
    val extras: Map<String, String> = emptyMap(),
)

data class InvitationCondition(
    val condition: String = "",
    val conditionAction: String = "",
    val target: String? = null,
)

data class InvitationRules(
    val isEnabled: Boolean = false,
)

data class WorkRange(
    val day: String = "",
    val from: String = "",
    val to: String = "",
)

data class WidgetConfigData(
    val name: String = "",
    val projectName: String = "",
    val projectId: String = "",
    val language: String? = null,
    val backgroundColor: String? = null,
    val backgroundImage: String? = null,
    val position: String? = null,
    val enabledChat: Boolean = true,
    val soundEnabled: Boolean? = null,
    val operators: List<Operator> = emptyList(),
    val channels: List<ChannelMessenger> = emptyList(),
    val rules: List<InvitationRules> = emptyList(),
    val workRange: List<WorkRange> = emptyList(),
    val timezone: String? = null,
    val showLink: Boolean = false,
    val buttonTextEnabled: Boolean? = null,
)

data class DialogClosedPayload(
    val status: Int,
)

data class DialogRatedPayload(
    val id: String,
    val formValue: String,
)

enum class ChatBackground(val id: String) {
    White("white"),
    Doodle("doodle"),
    Circles("circles"),
    Scribbles("scribbles");

    companion object {
        val DEFAULT = White
        val ALL = entries
        val PICKER = listOf(Doodle, Circles, Scribbles, White)

        fun fromId(value: String?): ChatBackground? =
            entries.firstOrNull { it.id.equals(value, ignoreCase = true) }
    }
}

fun isChatBackground(value: String?): Boolean = ChatBackground.fromId(value) != null

enum class ChatButtonPosition { Left, Right }

val CHAT_BUTTON_SCALES = listOf(70, 80, 90, 100)
