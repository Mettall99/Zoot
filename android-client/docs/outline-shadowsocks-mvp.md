# Outline-compatible Shadowsocks MVP for Android

## Why this protocol was added now

Zooot VPN already contains the Android VPN service, the sing-box/libbox runtime path, and experimental VLESS Reality support. VLESS Reality is intentionally left in the codebase, but it is not the current stabilization target because Android runtime/libbox startup for that path is still unstable.

This MVP adds a separate Outline-compatible Shadowsocks protocol next to the existing adapters. It does not remove VLESS Reality, does not change the bundled `sing-box-libbox.aar` or `libbox.so`, and does not reintroduce the old `net.clever-vpn` dependency. WireGuard continues to use its existing path.

## Demo `zoootconf://demo-token` flow

For the MVP, `zoootconf://demo-token` can be resolved locally into an Outline/Shadowsocks server config. The resolved protocol is `OUTLINE_SHADOWSOCKS`, the UI display name is **Outline Shadowsocks**, the config source is `zoootconf_demo`, and the protocol is connectable only when a local demo `ss://` access key is configured.

A real Outline-compatible Shadowsocks server is required for end-to-end testing. Do **not** commit a real production `ss://` access key, password, token, or server secret to this public repository.

Configure the demo access key locally in one of these ways before building a debug APK:

1. Environment variable:

   ```bash
   export ZOOOT_DEMO_SS_URI='ss://...'
   ```

2. `android-client/local.properties`:

   ```properties
   ZOOOT_DEMO_SS_URI=ss://...
   ```

3. `android-client/local.properties` alternative key:

   ```properties
   zooot.demo.ss.uri=ss://...
   ```

If no local demo access key is configured, resolving `zoootconf://demo-token` fails with this explicit UI error:

```text
Outline Shadowsocks server is not configured
```

## Supported access key formats

The Android client recognizes direct `ss://` connection strings and selects the `OUTLINE_SHADOWSOCKS` adapter. The same adapter is selected after `zoootconf://demo-token` resolves to an Outline/Shadowsocks config. The parser supports these formats:

1. SIP002 credentials before the server authority:

   ```text
   ss://BASE64(method:password)@host:port#name
   ```

2. Plain userinfo credentials:

   ```text
   ss://method:password@host:port#name
   ```

3. Outline-style full payload encoding:

   ```text
   ss://BASE64(method:password@host:port)#name
   ```

The optional fragment (`#name`) is used as a display tag when present. `plugin` and `plugin_opts` parameters are detected, but plugins are rejected for the MVP with this error:

```text
Shadowsocks plugins are not supported yet
```

## Supported encryption methods

The MVP accepts only these Shadowsocks AEAD methods:

- `chacha20-ietf-poly1305`
- `aes-256-gcm`
- `aes-128-gcm`

Other methods fail fast with:

```text
Unsupported Shadowsocks method: <method>
```

## Runtime path and generated sing-box config

Outline/Shadowsocks uses the existing Android sing-box/libbox runtime path, not WireGuard/GoBackend. The generated sing-box configuration contains:

- `log.level = info`
- a `tun` inbound tagged `tun-in`
- a `shadowsocks` outbound tagged `shadowsocks-out`
- `route.final = shadowsocks-out`
- `route.auto_detect_interface = true`

The TUN inbound uses interface `zooot0`, address `172.19.0.1/30`, MTU `9000`, `auto_route = true`, `strict_route = false`, and `stack = system`.

## Verify the server with the official Outline Client first

Before testing Zooot, verify the access key with the official Outline Client:

1. Install the official Outline Client on a device or desktop machine.
2. Add the same local `ss://` access key that will be used for `ZOOOT_DEMO_SS_URI`.
3. Connect with Outline Client.
4. Confirm traffic works through the server.
5. Only then build and test Zooot with the same key configured locally.

This separates server/access-key problems from Zooot Android integration issues.

## Manual testing with an Outline-compatible access key

### Direct `ss://` flow

1. Install a debug APK built from this branch.
2. Open the Android app.
3. Paste an Outline-compatible `ss://` access key into the connection link field.
4. Tap **Continue**.
5. Confirm Android VPN permission if prompted.
6. Tap **Connect**.

### Demo token flow

1. Configure `ZOOOT_DEMO_SS_URI` locally by environment variable or `android-client/local.properties`.
2. Build and install a debug APK.
3. Open the Android app.
4. Paste `zoootconf://demo-token` into the connection link field.
5. Tap **Continue**.
6. Select **Outline Shadowsocks** if it is not already selected.
7. Confirm Android VPN permission if prompted.
8. Tap **Connect**.

Expected behavior:

- The app selects **Outline Shadowsocks**.
- Direct `ss://` links still work.
- `zoootconf://demo-token` resolves to a local Outline/Shadowsocks config when `ZOOOT_DEMO_SS_URI` is set.
- If the demo key is missing, the UI shows `Outline Shadowsocks server is not configured` instead of a silent **Soon** state.
- The parser accepts the access key if it matches one of the supported formats.
- sing-box/libbox receives a Shadowsocks outbound config.
- If runtime startup succeeds, the VPN moves to the connected state.
- If runtime startup fails, the app shows a concrete `Shadowsocks runtime failed: <reason>` error.

## Logcat debugging

Use a filtered logcat session while reproducing the connection:

```bash
adb logcat -s ManualLinkFlow ZootApiClient OutlineShadowsocks SingBoxShadowsocksCore ZootVpnService LibboxRuntimeSupport
```

Useful safe lifecycle messages include:

- `zoootconf resolve start`
- `zoootconf resolve success protocol=outline_shadowsocks`
- `outline shadowsocks config available=true` or `outline shadowsocks config available=false`
- `protocol selected=outline_shadowsocks`
- `shadowsocks uri parse start`
- `shadowsocks uri parse success` or `shadowsocks uri parse failure`
- `shadowsocks config generated`
- `method=<method>`
- masked `server=<host>`
- `port=<port>`
- `runtime start called`
- `startOrReloadService called` when CommandServer is used
- `openTun called`
- `createTun success` or `createTun failure`
- `running=true` or `running=false`
- `lastError=<reason>`

The app must not log passwords, full `ss://` URIs, tokens, or keys.
