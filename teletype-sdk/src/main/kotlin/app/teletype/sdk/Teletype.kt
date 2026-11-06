/**
 * teletype-android-sdk
 *
 * Native Kotlin SDK for embedding the Teletype online chat into Android apps.
 * Reuses the Teletype web widget's backend protocol (REST + socket.io v2) —
 * no WebView required.
 *
 * Quick start:
 * ```kotlin
 * val client = TeletypeClient(
 *     context,
 *     TeletypeConfig(teletypeExternalId = "YOUR_EXTERNAL_ID", mode = TeletypeMode.Dev),
 * )
 * client.init()
 *
 * TeletypeProvider(client) {
 *     if (rememberTeletypeState().opened) {
 *         ChatScreen(onClose = { client.hide() })
 *     } else {
 *         /* your app */
 *         ChatWidget()
 *     }
 * }
 * ```
 */
package app.teletype.sdk
