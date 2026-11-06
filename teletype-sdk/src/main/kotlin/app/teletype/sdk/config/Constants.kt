package app.teletype.sdk.config

/**
 * WebSocket event names used by the Teletype backend (socket.io v2).
 * Kept byte-for-byte identical to the web widget so the same server works.
 */
object WsEvent {
    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val INIT_DATA_TO_PERSON = "init data to person"
    const val AUTH_PERSON = "auth person"
    const val MESSAGE_FROM_CLIENT = "message from client"
    const val MESSAGE_FROM_PERSON = "web widget message from person"
    const val AUTH_SUCCESS = "auth success"
    const val OPERATOR_TYPING = "operator typing"
    const val RECEIVED = "received"
    const val SEEN = "seen"
    const val PERSON_TYPING = "person typing"
    const val PERSON_STOP_TYPING = "person stop typing"
    const val OPERATOR_STOP_TYPING = "operator stop typing"
    const val OPERATOR_STATUS_CHANGE = "operator status change"
    const val PERSON_CLICK_WIDGET_CHANNEL = "person click widget channel"
    const val PERSON_RATE_APPEAL = "person rate appeal"
    const val APPEAL_STATUS_CHANGE = "appeal status change"
    const val PERSON_OPENED_WIDGET = "person opened widget"
    const val SET_PERSON_DATA = "set person data"
    const val SET_USER_DATA = "set user data"
}

/** Appeal status reported by `appeal status change`. 2 === closed by operator. */
const val APPEAL_STATUS_CLOSED = 2

const val INPUT_TYPING_TIMEOUT_MS = 60_000L
const val OUTPUT_TYPING_TIMEOUT_MS = 10_000L
const val RECEIVED_ACK_TIMEOUT_MS = 10_000L
const val CONFIG_LIFETIME_MS = 10 * 60 * 1000L
const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024

const val DEFAULT_PRIMARY_COLOR = "#0084ff"
const val DEFAULT_CHAT_TITLE = "Онлайн чат"

object StorageKeys {
    const val PERSON_ID = "person_id"
    const val APPEAL_ID = "appeal_id"
    const val CONFIG = "config"
    const val CONFIG_SAVE_TIME = "config-save-time"
    const val POPUP_STATE = "popup-state"
    const val WIDGET_INTERACTED = "widget-interacted"
    const val PENDING_MESSAGES = "pendingMessages"
}
