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

The Android client bundles and uses a real sing-box/libbox Android core for `xray_vless_reality` configs returned by `/api/v1/config/resolve-token`.

The Reality path now:

- validates non-empty Reality configs;
- accepts the backend JSON format and VLESS Reality URI format;
- converts the client config into a sing-box JSON config with a `tun` inbound and VLESS Reality outbound;
- starts/stops the Android `VpnService` runtime through libbox;
- reports success only after the libbox service has started and the adapter observes a running state.

#### Core dependency used

Gradle dependency:

```kotlin
implementation("net.clever-vpn:libbox-android:2.1.0")
```

This AAR is a prebuilt Android gomobile libbox package for sing-box. It exposes the `io.nekohasekai.libbox` API and includes the native `libbox.so` runtime used by `ZootVpnService`.

Required repositories are already configured in `android-client/settings.gradle.kts`:

```kotlin
google()
mavenCentral()
```

No local AAR is required for the default build. If Maven Central is unavailable in a restricted environment, place the same AAR at:

```text
android-client/app/libs/libbox-android-2.1.0.aar
```

and temporarily replace the Maven dependency with:

```kotlin
implementation(files("libs/libbox-android-2.1.0.aar"))
```

#### Build

Use JDK 17 and run from `android-client/`:

```bash
gradle testDebugUnitTest --no-daemon --max-workers=1 --stacktrace
gradle assembleDebug --no-daemon --max-workers=1
```

The project currently does not include a Gradle wrapper script; if one is added later, the equivalent Windows commands are:

```bat
.\gradlew.bat testDebugUnitTest --no-daemon --max-workers=1 --stacktrace
.\gradlew.bat assembleDebug --no-daemon --max-workers=1
```

#### Android service integration

`com.zooot.vpn.vpn.ZootVpnService` is declared as the app-owned `VpnService`. It creates the Android TUN file descriptor requested by libbox, starts the libbox `BoxService` with the generated sing-box JSON, protects core sockets from VPN routing, and closes both libbox and the TUN descriptor on disconnect/revoke.

#### Known limitations

- The implementation depends on the `io.nekohasekai.libbox` API exposed by `net.clever-vpn:libbox-android:2.1.0`.
- Unit tests mock `RealityCore`; full tunnel validation still requires an Android device/emulator with VPN permission granted.
- The local CI/container may fail dependency resolution if outbound access to Google Maven or Maven Central is blocked. In that case, use the local AAR path described above.

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
