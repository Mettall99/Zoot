# sing-box libbox AAR runtime requirements

`android-client/app/libs/sing-box-libbox.aar` is the only supported Android
libbox dependency for the Reality VPN runtime. The older
`net.clever-vpn:libbox-android` dependency must not be used because it can expose
only a command/diagnostic surface without the Android `VpnService`/TUN hooks that
Zooot needs.

## Required Android VPN/TUN API

Zooot treats libbox as runtime-capable when `classes.jar` exposes either of these
public API shapes in package `io.nekohasekai.libbox`.

### CommandServer service runtime

This is the supported shape for the currently bundled AAR:

- `Libbox.newCommandServer(CommandServerHandler, PlatformInterface): CommandServer`.
- `CommandServer.start()`.
- `CommandServer.startOrReloadService(String, OverrideOptions)`.
- `CommandServer.closeService()` or `CommandServer.close()`.
- `PlatformInterface.openTun(TunOptions)` returning `int` or `long`.

Zooot starts this backend by creating the command server, calling `start()`, then
calling `startOrReloadService(config, OverrideOptions)`. The VPN is considered
running only after `startOrReloadService(...)` returns successfully and libbox can
call `PlatformInterface.openTun(TunOptions)` to create the Android TUN with
`VpnService.Builder.establish()`.

### Direct BoxService runtime

A future AAR may instead expose this direct service shape, which remains a valid
alternative backend:

- `Libbox.newService(String, PlatformInterface): BoxService`, or a public
  `BoxService(String, PlatformInterface)` constructor.
- `BoxService.start()` and `BoxService.close()`.
- `PlatformInterface.openTun(TunOptions)` returning `int` or `long`.

## Inspection workflow

If `android-client/app/libs/sing-box-libbox.aar` is missing, there is no local AAR
payload to unpack and no `classes.jar` to inspect. In that case the runtime code
keeps an explicit unsupported-runtime error for incomplete command-server shapes,
such as missing `startOrReloadService(...)` or missing
`PlatformInterface.openTun(TunOptions)`:

`Bundled libbox exposes CommandServer only, but no Android VPN/TUN runtime API is available`

When the AAR is present, run the unit test
`BundledLibboxAarInspectionTest.bundledSingBoxLibboxAar_exposesSupportedRuntimeOrExplicitUnsupportedShape`.
It unpacks the AAR, loads `classes.jar`, prints the discovered public method
signatures, and verifies that `runtimeSupported=true` is set for either the
CommandServer service runtime or the direct BoxService runtime.

## APK packaging expectations

A valid runtime AAR must also package native libraries, including
`jni/arm64-v8a/libbox.so`, so the debug APK contains `lib/arm64-v8a/libbox.so`.
The Gradle dependency must point at the local AAR with
`implementation(files("libs/sing-box-libbox.aar"))` and must not include a
conflicting Maven libbox artifact.
