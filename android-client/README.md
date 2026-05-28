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

The Android client keeps the existing parser for `xray_vless_reality` configs returned by `/api/v1/config/resolve-token` and converts valid configs into sing-box JSON with a `tun` inbound and VLESS Reality outbound.

The current Maven dependency is still bundled:

```kotlin
implementation("net.clever-vpn:libbox-android:2.1.0")
```

Runtime inspection of that AAR shows that `io.nekohasekai.libbox.Libbox` exposes command/profile helpers such as `setup`, `checkConfig`, `newCommandClient`, `newCommandServer`, `newStandaloneCommandClient`, and `version`, but it does **not** expose a VPN service/runtime factory such as `BoxService`. The app therefore no longer attempts the invalid `Libbox.newService` reflection path.

Until the dependency is replaced with an official sing-box Android libbox build that exposes a service/runtime API, or a native sing-box runtime wrapper is added, Reality preparation/connection fails explicitly with:

```text
Current libbox dependency does not expose VPN runtime API
```

The Reality path now:

- validates non-empty Reality configs;
- accepts the backend JSON format and VLESS Reality URI format;
- converts the client config into a sing-box JSON config with a `tun` inbound and VLESS Reality outbound;
- keeps Android `VpnService` permission and foreground-service entry points;
- logs `Libbox.version()` and sanitized public method diagnostics when the runtime API is missing;
- reports success only if a future supported runtime starts and reports `running=true`;
- reports the explicit unsupported-core error instead of pretending the tunnel is connected.

#### Build

Use JDK 17 and run from the repository root:

```bash
./gradlew.bat clean testDebugUnitTest --no-daemon --max-workers=1 --stacktrace
./gradlew.bat clean assembleDebug --no-daemon --max-workers=1
```

#### Android service integration

`com.zooot.vpn.vpn.ZootVpnService` is declared as the app-owned `VpnService`. It owns the foreground-service lifecycle and contains the Android TUN builder/protection callbacks required by a proper sing-box Android runtime. With `net.clever-vpn:libbox-android:2.1.0`, startup stops before reporting success because the bundled API does not expose that runtime surface.

#### Known limitations

- `net.clever-vpn:libbox-android:2.1.0` is detected as unsupported for direct VPN runtime startup.
- Unit tests mock `RealityCore`; full tunnel validation still requires replacing the core dependency and testing on an Android device/emulator with VPN permission granted.
- The local CI/container may fail dependency resolution if outbound access to Google Maven or Maven Central is blocked.

## Logging policy

Reality logs must stay sanitized. Do not log full configs, UUIDs, public keys, short IDs, tokens, generated sing-box JSON, private keys, or backend secrets. Safe fields are limited to:

- `protocol=xray_vless_reality`
- `config_source`
- `config_available=true/false`
- `has_host`
- `has_port`
- `has_server_name`
- `has_flow`
- `core_name`
- `core_start_success`
- sanitized exception class/message

## Manual verification

1. Build and install the debug APK.
2. Grant Android VPN permission when prompted.
3. Resolve a token that returns `xray_vless_reality` with `config_source="xray_reality_env"` and `has_config=true`.
4. Connect using VLESS Reality.
5. On the VPS, confirm traffic reaches TCP/443, for example:

```bash
sudo tcpdump -ni any tcp port 443
```
