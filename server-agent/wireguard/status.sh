#!/usr/bin/env bash
set -euo pipefail

echo '=== wg show ==='
wg show || true

echo
echo '=== systemctl status wg-quick@wg0 --no-pager ==='
systemctl status wg-quick@wg0 --no-pager || true

echo
echo '=== UDP port 51821 ==='
if command -v ss >/dev/null 2>&1; then
  ss -lunp | grep -E ':51821\b' || echo 'UDP 51821 is not listening.'
else
  netstat -lunp 2>/dev/null | grep -E ':51821\b' || echo 'UDP 51821 is not listening.'
fi
