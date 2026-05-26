#!/usr/bin/env bash
set -euo pipefail

echo "[agent] checking dependencies"
command -v docker >/dev/null 2>&1 || echo "docker is not installed (install step placeholder)"
echo "[agent] MVP scaffold installed. Add protocol installers in next iteration."
