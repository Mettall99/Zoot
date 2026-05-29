# Zooot VPN config token architecture

Zooot issues one user-facing config URL per user:

```text
zoootconf://USER_TOKEN
```

The user never needs to see or paste a raw `ss://` Outline/Shadowsocks URI. Android sends the raw token to the backend resolve endpoint, receives the available protocol credentials, and connects with the recommended protocol.

## Why one shared `ss://` key is forbidden

A shared Outline/Shadowsocks key would make every user indistinguishable at the VPN server, so one leaked key compromises the whole service and cannot be revoked for only one user. The backend must create a separate Outline access key for each user/config token through the Outline Management API.

## Backend flow

1. `POST /api/v1/vpn/config-tokens` validates `userId`, requested `protocols`, and `serverId`.
2. The backend generates a cryptographically secure random token.
3. Only `HMAC-SHA256(token, VPN_CONFIG_TOKEN_SECRET)` and a short `token_prefix` are stored in `vpn_config_tokens`; the raw token is returned once in `zoootconf://...`.
4. For `outline_shadowsocks`, `OutlineManagementClient` calls `POST $OUTLINE_API_URL/access-keys`.
5. The returned `accessUrl` is encrypted at rest with `VPN_CREDENTIALS_ENCRYPTION_KEY` and stored in `vpn_protocol_credentials.encrypted_connect_uri` with the Outline `external_key_id`.
6. The backend tries to rename the Outline key to `Zooot user <userId> token <tokenPrefix>`. If a specific Outline API deployment does not support rename, the flow keeps the created credential and logs only safe metadata.

## Android resolve flow

Android keeps local `zoootconf://demo-token` as a development fallback via `ZOOOT_DEMO_SS_URI`. Any real `zoootconf://REAL_TOKEN` calls:

```http
POST /api/v1/vpn/config/resolve
Content-Type: application/json

{"token":"REAL_TOKEN"}
```

A successful response contains `recommendedProtocol` and a protocol list. For Outline, Android reads `connectUri`, maps `outline_shadowsocks` to `OUTLINE_SHADOWSOCKS`, and uses `OutlineShadowsocksProtocolAdapter`. Direct `ss://` and `vless://` links are still handled by the local scheme selector.

## Revoke flow

`POST /api/v1/vpn/config-tokens/{id}/revoke` marks the token and related protocol credentials as `revoked`. For `outline_shadowsocks`, the backend also calls:

```http
DELETE $OUTLINE_API_URL/access-keys/{externalKeyId}
```

After revoke, resolve returns `Config token revoked` and Android can show that message to the user.

## Required environment variables

```env
OUTLINE_API_URL=https://OUTLINE_HOST:OUTLINE_PORT/SECRET_PATH
OUTLINE_CERT_SHA256=change-me-cert-pin
VPN_CONFIG_TOKEN_SECRET=change-me-token-hmac-secret
VPN_CREDENTIALS_ENCRYPTION_KEY=change-me-32-bytes-or-base64-value
VPN_DEFAULT_SERVER_ID=outline-main-1
VPN_DEFAULT_SERVER_COUNTRY=DE
VPN_CONFIG_REQUIRE_ACTIVE_SUBSCRIPTION=false
```

Never commit real Outline API URLs, management secrets, token HMAC secrets, encryption keys, `ss://` URLs, or passwords.

## Curl smoke test

```bash
curl -sS -X POST http://localhost:8080/api/v1/vpn/config-tokens \
  -H 'content-type: application/json' \
  -d '{"userId":"demo-user","protocols":["outline_shadowsocks"],"serverId":"outline-main-1"}'
```

Copy the returned raw token from `configUrl` only in local testing, then resolve:

```bash
curl -sS -X POST http://localhost:8080/api/v1/vpn/config/resolve \
  -H 'content-type: application/json' \
  -d '{"token":"PASTE_RAW_TOKEN_HERE"}'
```

Revoke by database token id:

```bash
curl -sS -X POST http://localhost:8080/api/v1/vpn/config-tokens/TOKEN_UUID/revoke
```

## Secret-handling rules

- Store token hashes, never raw tokens.
- Store encrypted `ss://` credentials, never plaintext at rest.
- Do not log full tokens, `ss://` URLs, or Shadowsocks passwords.
- Logs may include only `tokenPrefix`, protocol names, and non-secret status information.
- Keep real `.env` files and curl output out of GitHub issues, PR descriptions, and screenshots.
