package app.teletype.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import app.teletype.sdk.core.TeletypeClient
import app.teletype.sdk.core.TeletypeState
import app.teletype.sdk.i18n.TeletypeStrings
import app.teletype.sdk.types.Message

val LocalTeletypeClient = staticCompositionLocalOf<TeletypeClient> {
    error("TeletypeClient is not provided. Wrap your UI in TeletypeProvider.")
}

@Composable
fun TeletypeProvider(
    client: TeletypeClient,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTeletypeClient provides client) {
        content()
    }
}

@Composable
fun rememberTeletypeClient(): TeletypeClient = LocalTeletypeClient.current

@Composable
fun rememberTeletypeState(): TeletypeState {
    val client = LocalTeletypeClient.current
    return client.stateFlow.collectAsState().value
}

@Composable
fun rememberMessages(): List<Message> = rememberTeletypeState().messages

@Composable
fun rememberUnreadCount(): Int = rememberTeletypeState().unreadCount

@Composable
fun rememberTeletypeStrings(): TeletypeStrings = rememberTeletypeState().strings
