#!/usr/bin/env bash
set -euo pipefail
[[ "${EUID}" -eq 0 ]] || { echo "This script must be run as root." >&2; exit 1; }
DELETE_FILES="false"
if [[ "${1:-}" == "--delete-files" ]]; then DELETE_FILES="true"; shift; fi
if [[ $# -ne 1 ]]; then echo "Usage: $0 [--delete-files] <client-name>" >&2; exit 1; fi
CLIENT_NAME="$1"; WG_IFACE="wg0"; CLIENTS_DIR="/etc/zooot/wireguard/clients"; REVOKED_DIR="${CLIENTS_DIR}/revoked"
CONF_PATH="${CLIENTS_DIR}/${CLIENT_NAME}.conf"; PUB_PATH="${CLIENTS_DIR}/${CLIENT_NAME}.public"; PRIV_PATH="${CLIENTS_DIR}/${CLIENT_NAME}.private"
[[ -f "$PUB_PATH" ]] || { echo "Missing public key file: $PUB_PATH" >&2; exit 1; }
PUBLIC_KEY="$(cat "$PUB_PATH")"
wg set "$WG_IFACE" peer "$PUBLIC_KEY" remove || true
wg-quick save "$WG_IFACE" >/dev/null || true
mkdir -p "$REVOKED_DIR"
if [[ "$DELETE_FILES" == "true" ]]; then rm -f "$CONF_PATH" "$PUB_PATH" "$PRIV_PATH"; else
  ts="$(date +%Y%m%d%H%M%S)"; for f in "$CONF_PATH" "$PUB_PATH" "$PRIV_PATH"; do [[ -f "$f" ]] && mv "$f" "$REVOKED_DIR/$(basename "$f").$ts"; done
fi
echo "revoked_client=${CLIENT_NAME}"
