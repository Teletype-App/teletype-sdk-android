package app.teletype.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.teletype.sdk.types.ChatButtonPosition

val CHAT_BUTTON_SCALES = listOf(70, 80, 90, 100)

@Composable
fun ChatButton(
    modifier: Modifier = Modifier,
    position: ChatButtonPosition? = null,
    scale: Int = 100,
    backgroundColor: Color? = null,
    bottom: Int = 24,
    side: Int = 20,
    onPress: (() -> Unit)? = null,
) {
    val client = rememberTeletypeClient()
    val state = rememberTeletypeState()
    val theme = mergeTheme(primaryFromConfig = state.primaryColor)
    val pos = position ?: if (state.config?.position == "left") ChatButtonPosition.Left else ChatButtonPosition.Right
    val factor = if (scale in CHAT_BUTTON_SCALES) scale / 100f else 1f
    val size = theme.buttonSize * factor
    val color = backgroundColor ?: parseColor(state.primaryColor) ?: theme.primaryColor
    val align = if (pos == ChatButtonPosition.Left) Alignment.BottomStart else Alignment.BottomEnd

    Box(
        modifier = modifier
            .padding(bottom = bottom.dp, start = side.dp, end = side.dp),
        contentAlignment = align,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .shadow(8.dp, CircleShape, ambientColor = color, spotColor = color)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { (onPress ?: { client.toggle() }).invoke() },
                contentAlignment = Alignment.Center,
            ) {
                ChatButtonIcon(size = size * 0.62f)
            }
            if (state.unreadCount > 0 && !state.opened) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size((20 * factor).dp)
                        .clip(CircleShape)
                        .background(theme.badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                        color = theme.badgeTextColor,
                        fontSize = (11 * factor).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun ChatWidget(
    hideButton: Boolean = false,
    position: ChatButtonPosition? = null,
    scale: Int = 100,
    bottom: Int = 24,
    side: Int = 20,
    modifier: Modifier = Modifier,
) {
    if (hideButton) return
    ChatButton(
        modifier = modifier,
        position = position,
        scale = scale,
        bottom = bottom,
        side = side,
    )
}
