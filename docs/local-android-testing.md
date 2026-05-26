# Local Android testing

## Build and test
### Variant A — via installed Gradle
```bash
cd android-client
gradle test
gradle assembleDebug
```

APK location:
`android-client/app/build/outputs/apk/debug/app-debug.apk`

### Variant B — via Android Studio
- Open the `android-client` folder.
- Wait for Gradle Sync to complete.
- Run **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Open deep link via ADB
```bash
adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"
```
