# Teletype Android SDK — example

Runnable Jetpack Compose demo of `teletype-sdk`. Functionally matches
`teletype-mobile-sdk-react-native/example` (setup screen, appearance controls,
language switch, file attachments).

```bash
cd ../..
./gradlew :example:demo-teletype-sdk:installDebug
```

Paste a widget `teletypeExternalId` (or the whole dashboard `<script>` snippet)
on the first screen. The demo talks to the **dev** cluster (`teletypeapp.com`)
by default — switch `TeletypeMode.Prod` in `MainActivity.kt` for production.
