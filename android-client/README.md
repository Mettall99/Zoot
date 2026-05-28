# Android client

## App flow (MVP)
1. Start screen with manual `zoootconf://...` link input.
2. Server selection screen.
3. Connection setup with protocol list.
4. Connected screen with timer and traffic placeholders.

Deep link is preserved:
`adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"`

Manual entry behaves the same as deep link and resolves token through backend.

## Protocols

### WireGuard

WireGuard remains the default protocol when a valid WireGuard config is returned by the backend. Existing WireGuard connection behavior is unchanged.

### VLESS Reality over TCP/443

The Android client now has a real `XrayRealityProtocolAdapter` path for `xray_vless_reality` configs returned by `/api/v1/config/resolve-token`:

- safely validates non-empty Reality configs;
- accepts the backend JSON format and VLESS Reality URI format;
- converts the client config into a sing-box JSON config with a `tun` inbound and VLESS Reality outbound;
- starts/stops through a `RealityCore` implementation;
- reports success only when the core reports that it is running.

#### Core used

The production target is the sing-box Android core (`libbox`) because it provides a maintained Android TUN/VpnService path and supports VLESS Reality. The repository intentionally does **not** vendor native sing-box AARs or `.so` files yet. Until an Android `RealityCore` implementation backed by libbox is bundled, the default adapter returns this explicit runtime/build integration error:

```text
Reality core is not bundled in this build
```

This is intentional: the app must not fake a connected state when no native tunnel/core is available.

#### How to bundle the native core

1. Add the sing-box/libbox Android AAR or local module to `android-client/app/build.gradle.kts`.
2. Implement `RealityCore` using the bundled libbox API and pass it to `XrayRealityProtocolAdapter` from `MainActivity`.
3. Register any VpnService required by the libbox integration in `AndroidManifest.xml`; `com.zooot.vpn.vpn.ZootVpnService` is already declared with `android.permission.BIND_VPN_SERVICE` for app-owned VPN integration.
4. Verify the APK contains ABI-specific native libraries for all supported ABIs, typically at least `arm64-v8a` and `armeabi-v7a`.
5. Run unit tests and an on-device connection test against a VPS with TCP/443 open.

Known limitation: without the native sing-box/libbox dependency, Reality is selectable when config exists but connection preparation fails with `Reality core is not bundled in this build` and the UI stays disconnected.

## Logging policy

Reality logs must stay sanitized. Do not log full configs, UUIDs, public keys, short IDs, tokens, or generated sing-box JSON. Safe fields are limited to protocol name, config source, config availability booleans, presence booleans for host/port/SNI/flow, core start status, and sanitized exception class/message.
