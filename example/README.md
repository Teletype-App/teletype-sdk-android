# Examples

The React Native SDK ships two demos (bare RN and Expo) because those are two
different JS runtimes. On Android there is a single native runtime, so this
folder contains one Compose app with the **same features** as those demos:

- paste a Teletype `external id` or install `<script>`
- chat title, color, background, launcher position and scale
- EN / RU language switch
- file attachments
- clear cache

## `demo-teletype-sdk`

```bash
./gradlew :example:demo-teletype-sdk:installDebug
```

Open the project in Android Studio and run the `demo-teletype-sdk` configuration.
The debug APK is written to `demo-teletype-sdk/build/outputs/apk/debug/`.
