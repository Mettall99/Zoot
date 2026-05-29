# Outline-compatible Shadowsocks MVP for Android

## Why this protocol was added now

Zooot VPN already contains the Android VPN service, the sing-box/libbox runtime path, and experimental VLESS Reality support. VLESS Reality is intentionally left in the codebase, but it is not the current stabilization target because Android runtime/libbox startup for that path is still unstable.

This MVP adds a separate Outline-compatible Shadowsocks protocol next to the existing adapters. It does not remove VLESS Reality, does not change the bundled `sing-box-libbox.aar` or `libbox.so`, and does not reintroduce the old `net.clever-vpn` dependency.

## Supported access key formats

The Android client recognizes `ss://` connection strings and selects the `OUTLINE_SHADOWSOCKS` adapter. The parser supports these formats:

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

## Manual testing with an Outline-compatible access key

1. Install a debug APK built from this branch.
2. Open the Android app.
3. Paste an Outline-compatible `ss://` access key into the connection link field.
4. Tap **Continue**.
5. Confirm Android VPN permission if prompted.
6. Tap **Connect**.

Expected behavior:

- The app selects **Outline Shadowsocks**.
- The parser accepts the access key if it matches one of the supported formats.
- sing-box/libbox receives a Shadowsocks outbound config.
- If runtime startup succeeds, the VPN moves to the connected state.
- If runtime startup fails, the app shows a concrete `Shadowsocks runtime failed: <reason>` error.

## Logcat debugging

Use a filtered logcat session while reproducing the connection:

```bash
adb logcat -s ManualLinkFlow OutlineShadowsocks SingBoxShadowsocksCore ZootVpnService LibboxRuntimeSupport
```

Useful safe lifecycle messages include:

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
