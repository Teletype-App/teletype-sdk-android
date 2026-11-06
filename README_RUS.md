# teletype-sdk-android

Нативный **Kotlin / Jetpack Compose SDK** для встраивания онлайн-чата [Teletype](https://teletype.app) в Android-приложения.

SDK использует тот же backend-протокол, что и **веб-виджет** Teletype (`/widget`) — REST API + `socket.io` (v2) — но рисует **нативный** интерфейс на Compose (без WebView). Публичный API совпадает с `_teletypeWidget.*` и с React Native / Flutter SDK.

> 🇬🇧 English docs: [README.md](./README.md)

## Быстрый старт

```kotlin
val client = TeletypeClient(
    context,
    TeletypeConfig(
        teletypeExternalId = "YOUR_EXTERNAL_ID",
        title = "Онлайн чат",
        primaryColor = "#0084ff",
        mode = TeletypeMode.Dev,
    ),
)
lifecycleScope.launch { client.init() }

TeletypeProvider(client) {
    if (rememberTeletypeState().opened) {
        ChatScreen(onClose = { client.hide() })
    } else {
        YourApp()
        ChatWidget()
    }
}
```

## Демо

`example/demo-teletype-sdk` — то же демо, что и в React Native SDK: вставка external id, цвет, фон, положение кнопки, язык EN/RU, вложения.

```bash
./gradlew :example:demo-teletype-sdk:installDebug
```

## Лицензия

MIT
