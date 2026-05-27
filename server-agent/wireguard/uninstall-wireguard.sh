#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "This script must be run as root." >&2
  exit 1
fi

WG_IFACE="wg0"
CLIENTS_DIR="/etc/zooot/wireguard/clients"

systemctl stop "wg-quick@${WG_IFACE}" || true
systemctl disable "wg-quick@${WG_IFACE}" || true

echo "WireGuard service ${WG_IFACE} stopped and disabled."

echo "Client configs are stored in ${CLIENTS_DIR}."
read -r -p "Delete client configs in ${CLIENTS_DIR}? [y/N]: " confirm
if [[ "${confirm}" =~ ^[Yy]$ ]]; then
  rm -rf "${CLIENTS_DIR}"
  echo "Client configs deleted."
else
  echo "Client configs were kept."
fi
