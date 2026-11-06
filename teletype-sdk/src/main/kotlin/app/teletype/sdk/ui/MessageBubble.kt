package app.teletype.sdk.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.teletype.sdk.core.extractUrls
import app.teletype.sdk.core.formatBytes
import app.teletype.sdk.core.getExtension
import app.teletype.sdk.i18n.TeletypeStrings
import app.teletype.sdk.i18n.formatRelativeTime
import app.teletype.sdk.types.ConversationRating
import app.teletype.sdk.types.FormType
import app.teletype.sdk.types.Message
import app.teletype.sdk.types.MessageStatus
import app.teletype.sdk.types.MessageType
import coil.compose.AsyncImage

@Composable
fun Composer(
    theme: TeletypeTheme,
    strings: TeletypeStrings,
    value: String,
    onChangeText: (String) -> Unit,
    onSend: (String) -> Unit,
    onTyping: (String) -> Unit,
    onStopTyping: () -> Unit,
    onAttachmentPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val canSend = value.trim().isNotEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.inputBackground)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (onAttachmentPress != null) {
            IconButton(onClick = onAttachmentPress) {
                Icon(Icons.Filled.AttachFile, contentDescription = strings.sendFile, tint = theme.mutedColor)
            }
        }
        BasicTextField(
            value = value,
            onValueChange = {
                onChangeText(it)
                if (it.trim().isNotEmpty()) onTyping(it) else onStopTyping()
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 120.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            textStyle = TextStyle(color = theme.inputTextColor, fontSize = 16.sp),
            cursorBrush = SolidColor(theme.primaryColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed)
                    onChangeText("")
                    onStopTyping()
                }
            }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(strings.composerPlaceholder, color = theme.mutedColor, fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
        IconButton(
            onClick = {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed)
                    onChangeText("")
                    onStopTyping()
                }
            },
            enabled = canSend,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = strings.send,
                tint = if (canSend) theme.primaryColor else theme.mutedColor,
            )
        }
    }
}

private val RATINGS = listOf(
    ConversationRating.Unsatisfactory to "😡",
    ConversationRating.Bad to "🙁",
    ConversationRating.Satisfactory to "😐",
    ConversationRating.Good to "🙂",
    ConversationRating.Excellent to "😍",
)

@Composable
fun MessageBubble(
    message: Message,
    theme: TeletypeTheme,
    strings: TeletypeStrings,
    language: String,
    onRate: (ConversationRating, String) -> Unit,
    onRetry: (String) -> Unit,
) {
    if (message.formType == FormType.Rating) {
        RatingForm(message, theme, strings, onRate)
        return
    }
    val isOwn = message.isOwn
    val mediaKind = resolveMediaKind(message)
    val emojiOnly = message.emojiOnly && message.messageType == MessageType.Text
    val bubbleColor = if (isOwn) theme.ownBubbleColor else theme.operatorBubbleColor
    val textColor = if (isOwn) theme.ownTextColor else theme.operatorTextColor
    val time = formatRelativeTime(message.date?.date, language, strings)
    val status = statusLabel(message, isOwn, time, strings)
    val failed = isOwn && message.status == MessageStatus.Failed && message.errorMessage == null
    val uploading = isOwn && (message.progress ?: 100) < 100 && message.errorMessage == null
    val visual = mediaKind == "image" || mediaKind == "video"
    val shape = RoundedCornerShape(
        topStart = theme.bubbleRadius.dp,
        topEnd = theme.bubbleRadius.dp,
        bottomStart = if (isOwn || emojiOnly || visual) theme.bubbleRadius.dp else 6.dp,
        bottomEnd = if (!isOwn || emojiOnly || visual) theme.bubbleRadius.dp else 6.dp,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isOwn) 48.dp else 12.dp,
                end = if (isOwn) 8.dp else 48.dp,
                top = 5.dp,
                bottom = 5.dp,
            ),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (emojiOnly || visual) androidx.compose.ui.graphics.Color.Transparent else bubbleColor,
                    )
                    .clickable(enabled = failed) { onRetry(message.tempId) }
                    .padding(
                        horizontal = if (visual || mediaKind == "file" || mediaKind == "audio") 0.dp else 15.dp,
                        vertical = if (visual || mediaKind == "file" || mediaKind == "audio") 0.dp else 11.dp,
                    ),
            ) {
                when (mediaKind) {
                    "image" -> MediaImage(message)
                    "video" -> MediaVideo(message, theme, strings)
                    "audio" -> MediaAudio(message, theme, strings, isOwn)
                    "file" -> MediaFile(message, theme, strings, isOwn)
                    else -> if (emojiOnly) {
                        Text(message.message.orEmpty(), fontSize = 44.sp)
                    } else {
                        LinkifiedText(message.message.orEmpty(), textColor)
                    }
                }
                if (uploading) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${message.progress}%", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
            Text(
                text = if (failed) strings.messageFailed else status,
                color = if (failed) theme.badgeColor else theme.mutedColor,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 5.dp, start = 4.dp, end = 4.dp)
                    .then(if (failed) Modifier.clickable { onRetry(message.tempId) } else Modifier),
            )
        }
    }
}

@Composable
private fun RatingForm(
    message: Message,
    theme: TeletypeTheme,
    strings: TeletypeStrings,
    onRate: (ConversationRating, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(theme.bubbleRadius.dp))
            .background(theme.operatorBubbleColor)
            .padding(12.dp),
    ) {
        Text(strings.ratingTitle, color = theme.operatorTextColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RATINGS.forEach { (value, emoji) ->
                val selected = message.ratingValue == value
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) theme.primaryColor else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = message.ratingValue == null) { onRate(value, message.tempId) }
                        .padding(6.dp),
                ) {
                    Text(emoji, fontSize = 26.sp)
                }
            }
        }
    }
}

@Composable
private fun LinkifiedText(text: String, color: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    val urls = remember(text) { extractUrls(text) }
    if (urls.isEmpty()) {
        Text(text, color = color, fontSize = 16.sp)
        return
    }
    androidx.compose.foundation.text.ClickableText(
        text = androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            urls.forEach { url ->
                val start = text.indexOf(url)
                if (start >= 0) {
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                        start,
                        start + url.length,
                    )
                    addStringAnnotation("URL", url, start, start + url.length)
                }
            }
        },
        style = TextStyle(color = color, fontSize = 16.sp),
        onClick = { offset ->
            // ClickableText is deprecated but widely available; open first matching url.
            urls.firstOrNull { text.indexOf(it) <= offset && offset <= text.indexOf(it) + it.length }?.let { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        },
    )
}

@Composable
private fun MediaImage(message: Message) {
    var open by remember { mutableStateOf(false) }
    val preview = message.attachmentPreviewUrl ?: message.attachmentUrl
    val full = message.attachmentUrl ?: preview
    AsyncImage(
        model = preview,
        contentDescription = message.attachment,
        modifier = Modifier
            .size(220.dp)
            .clickable(enabled = !full.isNullOrBlank()) { open = true },
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
    if (open && !full.isNullOrBlank()) {
        FullscreenImageViewer(
            url = full,
            contentDescription = message.attachment,
            onClose = { open = false },
        )
    }
}

@Composable
private fun MediaVideo(message: Message, theme: TeletypeTheme, strings: TeletypeStrings) {
    var open by remember { mutableStateOf(false) }
    val url = message.attachmentUrl
    val preview = message.attachmentPreviewUrl
    Box(
        modifier = Modifier
            .size(220.dp, 140.dp)
            .background(androidx.compose.ui.graphics.Color.Black)
            .clickable(enabled = !url.isNullOrBlank()) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        if (!preview.isNullOrBlank() && preview != url) {
            AsyncImage(
                model = preview,
                contentDescription = message.attachment,
                modifier = Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▶", color = androidx.compose.ui.graphics.Color.White, fontSize = 28.sp)
            Text(strings.mediaVideo, color = theme.headerTextColor, fontSize = 12.sp)
        }
    }
    if (open && !url.isNullOrBlank()) {
        FullscreenVideoViewer(url = url, onClose = { open = false })
    }
}

@Composable
private fun MediaAudio(message: Message, theme: TeletypeTheme, strings: TeletypeStrings, isOwn: Boolean) {
    val context = LocalContext.current
    val color = if (isOwn) theme.ownTextColor else theme.operatorTextColor
    Row(
        modifier = Modifier
            .padding(12.dp)
            .clickable {
                message.attachmentUrl?.let {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▶  ", color = color)
        Column {
            Text(strings.mediaAudio, color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(message.attachment ?: "", color = color.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MediaFile(message: Message, theme: TeletypeTheme, strings: TeletypeStrings, isOwn: Boolean) {
    val context = LocalContext.current
    val color = if (isOwn) theme.ownTextColor else theme.operatorTextColor
    val ext = getExtension(message.attachment ?: message.attachmentUrl).uppercase()
    Row(
        modifier = Modifier
            .padding(12.dp)
            .clickable {
                message.attachmentUrl?.let {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ext.take(4).ifBlank { "FILE" }, color = color, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(message.attachment ?: strings.attachmentFallback, color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            val size = message.size?.let { formatBytes(it, 1, strings) }
            if (!size.isNullOrBlank()) Text(size, color = color.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

private fun statusLabel(message: Message, isOwn: Boolean, time: String, strings: TeletypeStrings): String {
    if (!isOwn) return time
    when (message.errorMessage) {
        "fileSizeError" -> return strings.fileSizeError
        "fileFormatNotAllowed" -> return strings.fileFormatNotAllowed
        null -> Unit
        else -> return strings.uploadFailed
    }
    val progress = message.progress
    if (progress != null && progress < 100) return "${strings.uploading} ($progress%)"
    if (message.status == MessageStatus.Sending) return strings.messageSending
    if (message.status == MessageStatus.Failed) return strings.messageFailed
    val state = if (message.seen) strings.messageSeen else strings.messageDelivered
    return if (time.isNotBlank()) "$time. $state" else state
}

fun resolveMediaKind(message: Message): String? {
    val type = message.messageType
    if (type == MessageType.Image || type == MessageType.Gif) return "image"
    if (type == MessageType.Video) return "video"
    if (type == MessageType.Audio) return "audio"
    if (type == MessageType.Attachment) return "file"
    val name = message.attachment ?: message.attachmentUrl ?: return null
    val ext = getExtension(name)
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> "image"
        "mp4", "mov", "m4v", "webm", "mkv", "avi", "3gp" -> "video"
        "mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "amr" -> "audio"
        else -> if (!message.attachment.isNullOrBlank() || !message.attachmentUrl.isNullOrBlank()) "file" else null
    }
}
