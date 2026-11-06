package app.teletype.sdk.config

import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.MessageType

val IMAGE_MIME_TYPES = setOf(
    "image/gif", "image/jpeg", "image/pjpeg", "image/png",
    "image/vnd.microsoft.icon", "image/vnd.wap.wbmp", "image/webp",
    "image/heic", "image/heif",
)

val AUDIO_MIME_TYPES = setOf(
    "audio/basic", "audio/L24", "audio/mp4", "audio/aac", "audio/mpeg",
    "audio/ogg", "audio/vorbis", "audio/x-ms-wma", "audio/x-ms-wax",
    "audio/vnd.rn-realaudio", "audio/vnd.wave", "audio/webm", "audio/mp3",
)

val VIDEO_MIME_TYPES = setOf(
    "video/mpeg", "video/mp4", "video/ogg", "video/quicktime", "video/webm",
    "video/x-ms-wmv", "video/x-flv", "video/3gpp", "video/3gpp2",
)

val OTHER_MIME_TYPES = setOf(
    "video/3gp", "application/x-compressed", "audio/x-acc", "audio/ac3",
    "audio/x-aiff", "audio/aiff", "audio/x-au", "video/x-msvideo",
    "video/msvideo", "video/avi", "application/x-troff-msvideo",
    "image/bmp", "image/x-bmp", "image/x-bitmap", "image/x-xbitmap",
    "image/x-win-bitmap", "image/x-windows-bmp", "image/ms-bmp",
    "image/x-ms-bmp", "application/bmp", "application/x-bmp",
    "application/x-win-bitmap", "text/css", "text/plain", "text/html",
    "text/xml", "application/xml", "application/json", "text/json",
    "application/pdf", "application/octet-stream", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/zip", "application/x-zip", "application/x-zip-compressed",
    "application/x-rar", "application/rar", "application/x-rar-compressed",
    "application/x-tar", "application/gzip", "application/x-gzip",
    "audio/wav", "audio/x-wav", "audio/wave", "audio/x-m4a", "audio/x-flac",
    "video/x-ms-asf", "video/x-f4v", "text/rtf", "text/richtext",
)

val FILE_TYPES_LIST: Set<String> =
    OTHER_MIME_TYPES + AUDIO_MIME_TYPES + VIDEO_MIME_TYPES + IMAGE_MIME_TYPES

val EXTENSION_FILES_LIST = setOf(
    ".jpeg", ".jpg", ".png", ".gif", ".pjpeg", ".svg", ".tif", ".tiff",
    ".wbmp", ".webp", ".heic", ".heif", ".au", ".mp4a", ".mpga", ".oga",
    ".wma", ".wax", ".weba", ".mpeg", ".mp4", ".ogv", ".qt", ".webm",
    ".wmv", ".flv", ".3gp", ".3g2", ".aif", ".avi", ".bmp", ".docx",
    ".html", ".ico", ".jar", ".json", ".pdf", ".ppt", ".pptx", ".doc",
    ".rar", ".tar", ".txt", ".wav", ".xlsx", ".xls", ".xml", ".zip",
    ".mov", ".m4v", ".m4a", ".aac", ".mp3", ".ogg", ".opus", ".amr",
    ".caf", ".flac", ".mkv",
)

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "pjpeg")
private val VIDEO_EXTS = setOf("mp4", "mov", "m4v", "webm", "mkv", "avi", "3gp", "3g2", "ogv", "wmv", "flv")
private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "flac", "opus", "amr", "caf", "mpga", "mp4a", "weba")

fun fileExtension(name: String = ""): String {
    val base = name.split("?", "#").first()
    val idx = base.lastIndexOf('.')
    if (idx < 0 || idx == base.lastIndex) return ""
    return base.substring(idx).lowercase()
}

fun isAllowedUploadFile(file: LocalFile): Boolean {
    val type = file.type.lowercase()
    val ext = fileExtension(file.name)
    if (type.isNotEmpty() && type in FILE_TYPES_LIST) return true
    if (ext.isNotEmpty() && ext in EXTENSION_FILES_LIST) return true
    return false
}

fun messageTypeFromFile(file: LocalFile): MessageType {
    val type = file.type.lowercase()
    if (type in IMAGE_MIME_TYPES || type.startsWith("image/")) return MessageType.Image
    if (type in AUDIO_MIME_TYPES || type.startsWith("audio/")) return MessageType.Audio
    if (type in VIDEO_MIME_TYPES || type.startsWith("video/")) return MessageType.Video
    val ext = fileExtension(file.name).removePrefix(".")
    return when (ext) {
        in IMAGE_EXTS -> MessageType.Image
        in VIDEO_EXTS -> MessageType.Video
        in AUDIO_EXTS -> MessageType.Audio
        else -> MessageType.Attachment
    }
}
