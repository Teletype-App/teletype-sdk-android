package app.teletype.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.teletype.sdk.types.LocalFile
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    showClose: Boolean = true,
    onClose: (() -> Unit)? = null,
    placeholder: String? = null,
    title: String? = null,
    onAttachmentPress: (suspend () -> LocalFile?)? = null,
) {
    val client = rememberTeletypeClient()
    val state = rememberTeletypeState()
    val theme = mergeTheme(primaryFromConfig = state.primaryColor)
    val strings = state.strings
    val headerTitle = title ?: state.title
    var pendingFile by remember { mutableStateOf<LocalFile?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.operatorTyping) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex + if (state.operatorTyping) 1 else 0)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(theme.backgroundColor)) {
        ChatBackgroundLayer(state.chatBackground)
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.primaryColor)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showClose) {
                    IconButton(onClick = { onClose?.invoke() ?: client.hide() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.headerTextColor,
                        )
                    }
                }
                Text(
                    text = headerTitle,
                    color = theme.headerTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.messages, key = { it.tempId.ifBlank { it.id ?: it.hashCode().toString() } }) { message ->
                    MessageBubble(
                        message = message,
                        theme = theme,
                        strings = strings,
                        language = state.locale,
                        onRate = { value, tempId -> client.rateAppeal(value, tempId) },
                        onRetry = { client.retryMessage(it) },
                    )
                }
                if (state.operatorTyping) {
                    item("typing") { TypingIndicator(theme) }
                }
            }

            Composer(
                theme = theme,
                strings = strings,
                value = state.draft,
                onChangeText = { client.setMessage(it) },
                onSend = {
                    client.sendMessage(it)
                    client.setMessage("")
                },
                onTyping = { client.startTyping(it) },
                onStopTyping = { client.stopTyping() },
                onAttachmentPress = onAttachmentPress?.let {
                    {
                        scope.launch {
                            val file = it()
                            if (file != null) pendingFile = file
                        }
                    }
                },
            )
        }

        pendingFile?.let { file ->
            ModalSendFile(
                file = file,
                theme = theme,
                strings = strings,
                onClose = { pendingFile = null },
                onSend = { picked ->
                    pendingFile = null
                    scope.launch {
                        runCatching { client.uploadFile(picked) }
                    }
                },
            )
        }
    }
}

@Composable
private fun TypingIndicator(theme: TeletypeTheme) {
    Row(
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .padding(end = 4.dp)
                .background(theme.mutedColor, androidx.compose.foundation.shape.CircleShape),
        )
        Box(
            Modifier
                .size(10.dp)
                .padding(end = 4.dp)
                .background(theme.mutedColor, androidx.compose.foundation.shape.CircleShape),
        )
        Box(
            Modifier
                .size(10.dp)
                .background(theme.mutedColor, androidx.compose.foundation.shape.CircleShape),
        )
    }
}
