#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then echo "This script must be run as root." >&2; exit 1; fi

PRINT_CONFIG="false"
if [[ "${1:-}" == "--print-config" ]]; then PRINT_CONFIG="true"; shift; fi
if [[ $# -ne 1 ]]; then echo "Usage: $0 [--print-config] <client-name>" >&2; exit 1; fi
CLIENT_NAME="$1"
[[ "$CLIENT_NAME" =~ ^[a-zA-Z0-9._-]{1,80}$ ]] || { echo "Invalid client name" >&2; exit 1; }

WG_IFACE="wg0"; WG_PORT="51821"; ENDPOINT="31.59.45.197"; WG_SUBNET_PREFIX="10.66.66"; WG_NETWORK_CIDR="10.66.66.0/24"
ZOOOT_DIR="/etc/zooot/wireguard"; CLIENTS_DIR="${ZOOOT_DIR}/clients"; SERVER_PUB_KEY_FILE="${ZOOOT_DIR}/server.public"

mkdir -p "${CLIENTS_DIR}"; chmod 700 "${CLIENTS_DIR}"
CLIENT_CONF_PATH="${CLIENTS_DIR}/${CLIENT_NAME}.conf"; CLIENT_PRIV_PATH="${CLIENTS_DIR}/${CLIENT_NAME}.private"; CLIENT_PUB_PATH="${CLIENTS_DIR}/${CLIENT_NAME}.public"
[[ ! -e "${CLIENT_CONF_PATH}" ]] || { echo "Client config already exists: ${CLIENT_CONF_PATH}" >&2; exit 1; }

used_hosts="$(
  {
    wg show "${WG_IFACE}" allowed-ips 2>/dev/null | awk '{print $2}' || true
    find "${CLIENTS_DIR}" -maxdepth 1 -name '*.conf' -type f -print0 2>/dev/null | xargs -0 -r grep -hE '^Address\s*=\s*10\.66\.66\.[0-9]+/32$' || true
  } | sed -n 's#.*10\.66\.66\.\([0-9]\+\)/32#\1#p' | sort -n -u
)"

next_host=""
for host in $(seq 2 254); do grep -qx "${host}" <<<"${used_hosts}" || { next_host="${host}"; break; }; done
[[ -n "$next_host" ]] || { echo "No free IP addresses left in ${WG_NETWORK_CIDR}." >&2; exit 1; }
CLIENT_ADDR="${WG_SUBNET_PREFIX}.${next_host}/32"

umask 077
wg genkey | tee "${CLIENT_PRIV_PATH}" | wg pubkey > "${CLIENT_PUB_PATH}"
SERVER_PUBLIC_KEY="$(cat "${SERVER_PUB_KEY_FILE}")"; CLIENT_PRIVATE_KEY="$(cat "${CLIENT_PRIV_PATH}")"; CLIENT_PUBLIC_KEY="$(cat "${CLIENT_PUB_PATH}")"

cat >"${CLIENT_CONF_PATH}" <<CONF
[Interface]
PrivateKey = ${CLIENT_PRIVATE_KEY}
Address = ${CLIENT_ADDR}
DNS = 1.1.1.1

[Peer]
PublicKey = ${SERVER_PUBLIC_KEY}
Endpoint = ${ENDPOINT}:${WG_PORT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
CONF
chmod 600 "${CLIENT_CONF_PATH}" "${CLIENT_PRIV_PATH}" "${CLIENT_PUB_PATH}"

wg set "${WG_IFACE}" peer "${CLIENT_PUBLIC_KEY}" allowed-ips "${CLIENT_ADDR}"
wg-quick save "${WG_IFACE}" >/dev/null

echo "config_path=${CLIENT_CONF_PATH}"
echo "assigned_ip=${CLIENT_ADDR}"
echo "public_key=${CLIENT_PUBLIC_KEY}"
[[ "$PRINT_CONFIG" == "true" ]] && cat "${CLIENT_CONF_PATH}"
