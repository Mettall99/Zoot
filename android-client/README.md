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


## Modern MVP test UI
- Header with **status badge** (Ready / ReadyToConnect / Connected / Disconnected / Error).
- Rounded **summary cards** for backend and connection data.
- **Editable backend URL** with compact Save action.
- Modern action layout: primary **Connect**, plus secondary Load config / Retry and outlined Disconnect.

## WireGuard MVP
- Backend `/api/v1/config/resolve-token` must return `wireguard` protocol with non-empty `config`.
- On Android, tapping **Connect** for selected `wireguard` requests VPN permission via `VpnService.prepare(...)` and then starts tunnel.
- Verify VPS tunnel status with `wg show` (expect latest handshake + transfer bytes after connect).
