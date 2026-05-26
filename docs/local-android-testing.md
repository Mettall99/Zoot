# Local Android testing

## Backend for test
`http://31.59.45.197:8080`

## Build and test
### Variant A — via installed Gradle
```bash
cd android-client
gradle test
gradle assembleDebug
```

APK location:
`android-client/app/build/outputs/apk/debug/app-debug.apk`

## Install APK
```bash
cd android-client
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Variant B — via Android Studio
- Open the `android-client` folder.
- Wait for Gradle Sync to complete.
- Run **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Open deep link via ADB
```bash
adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"
```
