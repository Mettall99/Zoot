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


## Expected test UI values
After opening `zoootconf://demo-token` and successful config load:
- backend URL: `http://31.59.45.197:8080`
- email: `demo@zooot.local`
- tariff: `Demo Monthly`
- country: `DE`
- city: `Frankfurt`
- selected protocol: `amneziawg`
- status: `ReadyToConnect` (or `Connected` after pressing Connect)

Backward-compatibility note:
- Старый Android UI продолжает работать, даже если backend возвращает дополнительное поле `protocols[].config`.


## Modern MVP test UI
- Header with **status badge** (Ready / ReadyToConnect / Connected / Disconnected / Error).
- Rounded **summary cards** for backend and connection data.
- **Editable backend URL** with compact Save action.
- Modern action layout: primary **Connect**, plus secondary Load config / Retry and outlined Disconnect.

## WireGuard real adapter check
1. Load token config and confirm `WireGuard config: available`.
2. Tap **Connect** and accept VPN permission prompt.
3. On VPS run `wg show` and verify peer has `latest handshake` and `transfer` counters updating.
4. If `latest handshake` and `transfer` are present, real VPN connect is successful.
5. In app UI status badge must show `Connected` (not `Error`) and error card must be hidden.

## Device-level provisioning check
Use resolve-token payload with `device_id` and `device_name` to validate per-device config source.
