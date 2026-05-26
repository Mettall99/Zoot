# Zooot Android Client (MVP skeleton)

## What is included
- MainActivity with simple Android Views UI, config details, and fake connect flow.
- Deep link parsing for `zoootconf://TOKEN` and `zoootconf://connect?token=TOKEN`.
- Token resolve against backend `http://31.59.45.197:8080`.
- ProtocolSelector server/protocol pick.
- FakeVpnProtocolAdapter (no real VPN adapters).
- ZootVpnService stub.
- Local unit tests.

## Local checks
### Variant A — via installed Gradle
```bash
cd android-client
gradle test
gradle assembleDebug
```

APK output:
`android-client/app/build/outputs/apk/debug/app-debug.apk`

### Install APK
```bash
cd android-client
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Variant B — via Android Studio
- Open the `android-client` folder.
- Wait for Gradle Sync to complete.
- Run **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Deep link test
```bash
adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"
```

## Debug backend for testing
`http://31.59.45.197:8080`


## Expected MVP UI after deep link
- Backend URL: `http://31.59.45.197:8080`
- Deep link: `zoootconf://demo-token`
- Email: `demo@zooot.local`
- Tariff: `Demo Monthly`
- Country: `DE`
- City: `Frankfurt`
- Protocol: `amneziawg`
- Status: `ReadyToConnect` (or `Connected` after pressing Connect)
- Buttons: `Load config`, `Connect`, `Disconnect`, `Retry`
