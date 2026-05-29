# sing-box libbox AAR runtime requirements

`android-client/app/libs/sing-box-libbox.aar` is the only supported Android
libbox dependency for the Reality VPN runtime. The older
`net.clever-vpn:libbox-android` dependency must not be used because it can expose
only the sing-box command/diagnostic surface and not a `VpnService`/TUN runtime.

## Required Android VPN/TUN API

Zooot treats libbox as runtime-capable only when `classes.jar` exposes all of the
following public API shape in package `io.nekohasekai.libbox`:

- `Libbox.newService(String, PlatformInterface): BoxService`, or a public
  `BoxService(String, PlatformInterface)` constructor.
- `BoxService.start()` and `BoxService.close()`.
- `PlatformInterface.openTun(TunOptions)` returning `int` or `long`.

`CommandServer`, `newCommandServer(...)`, `startOrReloadService(...)`, and related
classes are diagnostic/control APIs only. They are not a replacement for the
Android `VpnService`/TUN runtime and must never be used as a fallback backend.

## Inspection workflow

If `android-client/app/libs/sing-box-libbox.aar` is missing, there is no local AAR
payload to unpack and no `classes.jar` to inspect. In that case the runtime code
keeps the explicit unsupported-runtime error when only `CommandServer` is present:

`Bundled libbox exposes CommandServer only, but no Android VPN/TUN runtime API is available`

When the AAR is present, run the unit test
`BundledLibboxAarInspectionTest.bundledSingBoxLibboxAar_exposesRealRuntimeOrExplicitCommandServerOnlyError`.
It unpacks the AAR, loads `classes.jar`, prints the discovered public method
signatures, and verifies that `runtimeSupported=true` is set only for the real
`BoxService`/`newService` + `PlatformInterface.openTun(...)` runtime API.

## APK packaging expectations

A valid runtime AAR must also package native libraries, including
`jni/arm64-v8a/libbox.so`, so the debug APK contains `lib/arm64-v8a/libbox.so`.
The Gradle dependency must point at the local AAR with
`implementation(files("libs/sing-box-libbox.aar"))` and must not include a
conflicting Maven libbox artifact.
