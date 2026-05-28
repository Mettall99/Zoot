# Xray VLESS Reality TCP/443 fallback

Zooot can advertise `xray_vless_reality` as a TCP/443 fallback when WireGuard UDP is unstable. WireGuard stays enabled; Android only treats Reality as available when `/api/v1/config/resolve-token` returns a non-empty Reality config.

## 1. Install Xray on the VPS

```bash
sudo bash -c "$(curl -L https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" @ install
xray version
```

Generate Reality key material and a short id:

```bash
xray x25519
openssl rand -hex 8
uuidgen
```

Keep the X25519 **private key** only on the VPS. Put the X25519 **public key**, short id, SNI, and client UUID in the backend environment.

## 2. Generate the inbound config

The repository includes a config generator for the TCP/443 VLESS Reality inbound:

```bash
sudo XRAY_REALITY_PRIVATE_KEY='PRIVATE_KEY_FROM_xray_x25519' \
  XRAY_REALITY_SHORT_ID='SHORT_ID_HEX' \
  XRAY_REALITY_SERVER_NAME='www.cloudflare.com' \
  XRAY_VLESS_UUID='CLIENT_UUID_RETURNED_BY_BACKEND_OR_SHARED_UUID' \
  server-agent/xray/generate-inbound-config.sh
```

The script writes `/usr/local/etc/xray/config.json` with:

- inbound protocol: `vless`
- transport: `tcp`
- port: `443`
- security: `reality`
- client flow: `xtls-rprx-vision`

Restart Xray after writing the config:

```bash
sudo systemctl restart xray
sudo systemctl enable xray
```

## 3. Firewall requirements

Allow TCP/443 to the VPS. Keep existing WireGuard UDP rules if you still want WireGuard as the preferred healthy protocol.

```bash
sudo ufw allow 443/tcp
sudo ufw allow 51821/udp
sudo ufw status
```

For raw iptables deployments:

```bash
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
```

## 4. Backend environment

Set these variables on the backend API host. Do not set private keys in the backend; only the public Reality key is needed for client config generation.

```bash
XRAY_REALITY_ENABLED=true
XRAY_REALITY_HOST=31.59.45.197
XRAY_REALITY_PORT=443
XRAY_REALITY_PUBLIC_KEY='PUBLIC_KEY_FROM_xray_x25519'
XRAY_REALITY_SHORT_ID='SHORT_ID_HEX'
XRAY_REALITY_SERVER_NAME='www.cloudflare.com'
XRAY_REALITY_FLOW='xtls-rprx-vision'
# Optional: use a shared UUID that also exists in /usr/local/etc/xray/config.json.
# If omitted, the backend uses the subscribed user's UUID as the VLESS user id.
XRAY_REALITY_UUID='CLIENT_UUID_IN_XRAY_CLIENTS_LIST'
```

The resolve-token response returns `config_source: "xray_reality_env"` only when the Reality environment is complete.

## 5. Verification commands

Check the Xray service and TCP/443 listener:

```bash
sudo systemctl status xray --no-pager
sudo ss -tulpn | grep ':443'
```

Check that the backend returns a non-empty Reality config without printing tokens in shell history by reading the token from stdin:

```bash
read -rsp 'Zooot token: ' ZOOOT_TOKEN; echo
curl -sS http://127.0.0.1:8080/api/v1/config/resolve-token \
  -H 'content-type: application/json' \
  --data "{\"token\":\"${ZOOOT_TOKEN}\",\"device_id\":\"android-test\",\"device_name\":\"Android test\"}" \
  | jq '.servers[].protocols[] | select(.type=="xray_vless_reality") | {type, port, config_source, has_config:(.config != null and (.config|length) > 0)}'
```

Expected result: `has_config` is `true`, `port` is `443`, and `config_source` is `xray_reality_env`.

## 6. Android client config compatibility

Android accepts the existing backend JSON config returned in the `config` string for `xray_vless_reality` and also accepts the embedded `vless://` URI. No backend API change is required.

Required client fields are:

- `protocol: "xray_vless_reality"`
- `host`
- `port` (defaults to `443` only for URI parsing; JSON should include it)
- `uuid`
- `public_key` or `publicKey`
- `short_id` or `shortId`
- `server_name` or `serverName`
- optional `flow` (`xtls-rprx-vision` for the generated VPS inbound)
- optional `fingerprint` (defaults to `chrome`)

The Android adapter converts these fields to a sing-box config with a TUN inbound and VLESS Reality outbound. Current source does not vendor sing-box native libraries; builds without that dependency fail Reality preparation with `Reality core is not bundled in this build` instead of showing a false connected state.
