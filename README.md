# teletype-sdk-android

Native **Kotlin / Jetpack Compose SDK** for embedding the [Teletype](https://teletype.app) online chat into Android apps.

It reuses the same backend protocol as the Teletype **web widget** (`/widget`) — REST API + `socket.io` (v2) — but renders a **native** Compose chat UI (no WebView). The public surface mirrors the web widget's `_teletypeWidget.*` API and the React Native / Flutter SDKs.

> 🇷🇺 Документация на русском: [README_RUS.md](./README_RUS.md)

---

## Features

- Real-time messaging over `socket.io` v2 (typing, seen/received receipts)
- Drop-in `ChatWidget` (floating button) and `ChatScreen` as a regular Activity/Compose screen
- Headless core (`TeletypeClient`) + `StateFlow` for custom UIs
- Message history, offline pending queue, auto-reconnect
- Operator rating form, service/bot messages, attachments (image, video, audio, files)
- Unread badge and the same event names as the web `teletype.*` events
- Persisted session (person id + appeal id) via `SharedPreferences`
- English / Russian UI strings, runtime locale switch

---

## Requirements

- Android 7.0 (API 24)+
- JDK 17
- Android SDK 35
- Kotlin 2.1 / Jetpack Compose

---

## Installation

This repo is an Android library + example app. Open it in Android Studio or add the `:teletype-sdk` module to your project:

```kotlin
// settings.gradle.kts
include(":teletype-sdk")

// app/build.gradle.kts
dependencies {
    implementation(project(":teletype-sdk"))
}
```

Maven coordinates will be published later.

The Teletype backend runs **socket.io v2**. The library already depends on `io.socket:socket.io-client:1.0.2` (Engine.IO v3).

---

## Quick start

```kotlin
val client = TeletypeClient(
    context,
    TeletypeConfig(
        teletypeExternalId = "YOUR_EXTERNAL_ID",
        title = "Онлайн чат",
        primaryColor = "#0084ff",
        mode = TeletypeMode.Prod, // or TeletypeMode.Dev
    ),
)
lifecycleScope.launch { client.init() }

setContent {
    TeletypeProvider(client) {
        val state = rememberTeletypeState()
        if (state.opened) {
            ChatScreen(
                onClose = { client.hide() },
                onAttachmentPress = { pickFile() },
            )
        } else {
            YourApp()
            ChatWidget(position = ChatButtonPosition.Right, scale = 100)
        }
    }
}
```

`teletypeExternalId` is the widget external id from the channel install snippet in the Teletype dashboard.

---

## Public API

| Method | Web equivalent | Description |
| --- | --- | --- |
| `client.show()` | `_teletypeWidget.show()` | Open the chat UI |
| `client.hide()` | `_teletypeWidget.hide()` | Close the chat UI |
| `client.toggle()` | — | Toggle the chat UI |
| `client.setMessage(text)` | `_teletypeWidget.setMessage()` | Prefill the composer |
| `client.setPersonData(info)` | `_teletypeWidget.setPersonData()` | Attach visitor data |
| `client.greetingMessages(...)` | `_teletypeWidget.greetingMessages()` | Inject bot greeting messages |
| `client.sendMessage(text)` | — | Send a message programmatically |
| `client.uploadFile(file)` | — | Upload + send an attachment |
| `client.rateAppeal(value, tempId?)` | — | Submit a conversation rating |
| `client.markSeen()` | — | Mark inbound messages as seen |
| `client.setTitle(text)` / `setPrimaryColor` / `setChatBackground` | — | Appearance |
| `client.setLocale(...)` | — | Switch UI language |

Subscribe with `client.on<T>("messageReceived") { ... }`. State is also exposed as `client.stateFlow`.

---

## Clusters: `Dev` vs `Prod`

| `mode` | apiUrl | wsUrl |
| --- | --- | --- |
| `Dev` | `https://api.teletypeapp.com/` | `https://websocket-widget.teletypeapp.com` |
| `Prod` (default) | `https://api.teletype.app/` | `https://websocket-widget.teletype.app/` |

---

## Example app

A runnable demo lives under [`example/demo-teletype-sdk`](./example/demo-teletype-sdk). It matches the React Native example: connect a channel by pasting an external id / install snippet, then tweak title, color, background, launcher position/scale, language, and file attachments.

```bash
# from the repo root
./gradlew :example:demo-teletype-sdk:installDebug
```

Or open the project in Android Studio and run the `demo-teletype-sdk` configuration.

---

## License

MIT
