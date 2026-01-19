#!/usr/bin/env bash
set -euo pipefail

HOST="${1:-0.0.0.0}"
PORT="${2:-3001}"

# OFBiz default HTTPS connector (from framework/catalina/ofbiz-component.xml) is 8443.
# We proxy external preview traffic on $PORT to OFBiz's internal HTTPS listener.
UPSTREAM_URL="${OFBIZ_UPSTREAM_URL:-https://127.0.0.1:8443}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[kavia] Starting OFBiz in background (Gradle)..."
(
  # IMPORTANT: task name includes arguments; must be quoted as a single Gradle task name.
  ./gradlew --no-daemon "ofbiz --start"
) &
OFBIZ_PID="$!"

cleanup() {
  echo "[kavia] Shutdown requested; stopping processes..."
  if kill -0 "$OFBIZ_PID" >/dev/null 2>&1; then
    kill "$OFBIZ_PID" >/dev/null 2>&1 || true
  fi
  wait "$OFBIZ_PID" >/dev/null 2>&1 || true
}
trap cleanup INT TERM EXIT

echo "[kavia] Starting reverse proxy on ${HOST}:${PORT} -> ${UPSTREAM_URL}"
python3 "${SCRIPT_DIR}/ofbiz_reverse_proxy.py" \
  --listen-host "${HOST}" \
  --listen-port "${PORT}" \
  --upstream "${UPSTREAM_URL}"
