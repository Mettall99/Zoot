#!/usr/bin/env bash
set -euo pipefail

: "${XRAY_REALITY_PRIVATE_KEY:?set XRAY_REALITY_PRIVATE_KEY from xray x25519}"
: "${XRAY_REALITY_SHORT_ID:?set XRAY_REALITY_SHORT_ID, for example openssl rand -hex 8}"
: "${XRAY_REALITY_SERVER_NAME:=www.cloudflare.com}"
: "${XRAY_REALITY_DEST:=${XRAY_REALITY_SERVER_NAME}:443}"
: "${XRAY_REALITY_PORT:=443}"
: "${XRAY_VLESS_UUID:?set XRAY_VLESS_UUID to the client UUID returned by the backend, or a dedicated shared fallback UUID}"
: "${XRAY_OUTPUT:=/usr/local/etc/xray/config.json}"

umask 077
mkdir -p "$(dirname "$XRAY_OUTPUT")"
cat > "$XRAY_OUTPUT" <<JSON
{
  "log": {
    "loglevel": "warning"
  },
  "inbounds": [
    {
      "tag": "zooot-vless-reality-443",
      "listen": "0.0.0.0",
      "port": ${XRAY_REALITY_PORT},
      "protocol": "vless",
      "settings": {
        "clients": [
          {
            "id": "${XRAY_VLESS_UUID}",
            "flow": "xtls-rprx-vision",
            "email": "zooot-fallback"
          }
        ],
        "decryption": "none"
      },
      "streamSettings": {
        "network": "tcp",
        "security": "reality",
        "realitySettings": {
          "show": false,
          "dest": "${XRAY_REALITY_DEST}",
          "xver": 0,
          "serverNames": ["${XRAY_REALITY_SERVER_NAME}"],
          "privateKey": "${XRAY_REALITY_PRIVATE_KEY}",
          "shortIds": ["${XRAY_REALITY_SHORT_ID}"]
        }
      }
    }
  ],
  "outbounds": [
    { "protocol": "freedom", "tag": "direct" },
    { "protocol": "blackhole", "tag": "blocked" }
  ]
}
JSON
chmod 600 "$XRAY_OUTPUT"
echo "Wrote Xray VLESS Reality inbound to $XRAY_OUTPUT"
