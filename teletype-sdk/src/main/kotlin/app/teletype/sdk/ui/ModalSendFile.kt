package app.teletype.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.teletype.sdk.config.messageTypeFromFile
import app.teletype.sdk.core.formatBytes
import app.teletype.sdk.i18n.TeletypeStrings
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.MessageType
import coil.compose.AsyncImage

@Composable
fun ModalSendFile(
    file: LocalFile,
    theme: TeletypeTheme,
    strings: TeletypeStrings,
    onClose: () -> Unit,
    onSend: (LocalFile) -> Unit,
) {
    val kind = messageTypeFromFile(file)
    val title = when (kind) {
        MessageType.Image -> strings.sendImage
        MessageType.Video -> strings.sendVideo
        MessageType.Audio -> strings.sendAudio
        else -> strings.sendFile
    }
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.inputBackground)
                .padding(16.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = theme.inputTextColor)
            when (kind) {
                MessageType.Image -> AsyncImage(
                    model = file.uri,
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                else -> {
                    Text(
                        file.name,
                        modifier = Modifier.padding(top = 12.dp),
                        color = theme.inputTextColor,
                    )
                    file.size?.let {
                        Text(formatBytes(it, 1, strings), color = theme.mutedColor, fontSize = 13.sp)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text(strings.cancel, color = theme.mutedColor)
                }
                Button(
                    onClick = { onSend(file) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                ) {
                    Text(strings.send)
                }
            }
        }
    }
}
