#!/usr/bin/env bash
set -euo pipefail

SERVER_ID="${SERVER_ID:-srv_demo}"
LOAD_PERCENT="${LOAD_PERCENT:-12}"
ACTIVE_USERS="${ACTIVE_USERS:-3}"

cat <<JSON
{
  "server_id": "${SERVER_ID}",
  "status": "online",
  "load_percent": ${LOAD_PERCENT},
  "active_users": ${ACTIVE_USERS},
  "protocols": [
    {"type": "wireguard", "health_status": "healthy"},
    {"type": "amneziawg", "health_status": "unknown"}
  ]
}
JSON
