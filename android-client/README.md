# Zooot Android Client (MVP skeleton)

## Что реализовано в PR #5
- API-слой для `POST /api/v1/config/resolve-token`.
- Deep link flow: `zoootconf://demo-token`, `zoootconf://connect?token=...`, `zoootconf://connect/...`.
- UI state machine: Idle → TokenReceived → LoadingConfig → ConfigLoaded → SelectingProtocol → ReadyToConnect → Connecting → Connected/Error.
- Fake connect/disconnect через `FakeVpnProtocolAdapter` (без реального VPN туннеля).
- Debug backend URL конфиг (`DebugConfig.BACKEND_BASE_URL`).

## Backend URL
- Emulator: `http://10.0.2.2:3000`
- Физическое устройство: `http://LOCAL_PC_IP:3000`

Быстрая смена адреса:
- Измените `android-client/app/src/main/java/com/zooot/vpn/app/DebugConfig.kt`.

## Debug cleartext HTTP
- Для debug используйте `app/src/debug/res/xml/network_security_config.xml`.
- Для release cleartext HTTP не должен включаться.

## Build/Test
```bash
cd android-client
chmod +x ./gradlew
./gradlew test
./gradlew assembleDebug
```

APK:
`app/build/outputs/apk/debug/app-debug.apk`

Установка:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Deep links:
```bash
adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"
adb shell am start -a android.intent.action.VIEW -d "zoootconf://connect?token=demo-token"
adb shell am start -a android.intent.action.VIEW -d "zoootconf://connect/demo-token"
```
