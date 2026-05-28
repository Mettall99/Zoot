#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if command -v mise >/dev/null 2>&1; then
  JAVA17_HOME="$(mise where java@17.0.2 2>/dev/null || true)"
  if [[ -n "$JAVA17_HOME" && -x "$JAVA17_HOME/bin/java" ]]; then
    export JAVA_HOME="$JAVA17_HOME"
  fi
fi

exec gradle -p "$ROOT_DIR/android-client" "$@"
