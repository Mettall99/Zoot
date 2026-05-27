#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "This script must be run as root." >&2
  exit 1
fi

ENDPOINT="31.59.45.197"
WG_PORT="51821"
WG_IFACE="wg0"
WG_NETWORK_CIDR="10.66.66.0/24"
WG_SERVER_ADDR="10.66.66.1/24"
ZOOOT_DIR="/etc/zooot/wireguard"
WG_DIR="/etc/wireguard"
WG_CONF="${WG_DIR}/${WG_IFACE}.conf"
SERVER_PRIV_KEY="${ZOOOT_DIR}/server.private"
SERVER_PUB_KEY="${ZOOOT_DIR}/server.public"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y wireguard iptables qrencode curl

sysctl -w net.ipv4.ip_forward=1
cat >/etc/sysctl.d/99-zooot-wireguard.conf <<SYSCTL
net.ipv4.ip_forward=1
SYSCTL
sysctl --system >/dev/null

mkdir -p "${ZOOOT_DIR}" "${ZOOOT_DIR}/clients" "${WG_DIR}"
chmod 700 "${ZOOOT_DIR}" "${ZOOOT_DIR}/clients"

if [[ ! -s "${SERVER_PRIV_KEY}" || ! -s "${SERVER_PUB_KEY}" ]]; then
  umask 077
  wg genkey | tee "${SERVER_PRIV_KEY}" | wg pubkey > "${SERVER_PUB_KEY}"
fi

SERVER_PRIVATE_KEY="$(cat "${SERVER_PRIV_KEY}")"
DEFAULT_IFACE="$(ip route list default | awk '{print $5}' | head -n1)"
if [[ -z "${DEFAULT_IFACE}" ]]; then
  echo "Failed to detect default network interface." >&2
  exit 1
fi

cat >"${WG_CONF}" <<CONF
[Interface]
Address = ${WG_SERVER_ADDR}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIVATE_KEY}
SaveConfig = true
PostUp = iptables -A FORWARD -i ${WG_IFACE} -j ACCEPT; iptables -A FORWARD -o ${WG_IFACE} -j ACCEPT; iptables -t nat -A POSTROUTING -s ${WG_NETWORK_CIDR} -o ${DEFAULT_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i ${WG_IFACE} -j ACCEPT; iptables -D FORWARD -o ${WG_IFACE} -j ACCEPT; iptables -t nat -D POSTROUTING -s ${WG_NETWORK_CIDR} -o ${DEFAULT_IFACE} -j MASQUERADE
CONF
chmod 600 "${WG_CONF}"

systemctl enable "wg-quick@${WG_IFACE}"
systemctl restart "wg-quick@${WG_IFACE}"

echo "WireGuard installed. Endpoint: ${ENDPOINT}:${WG_PORT}"
wg show
