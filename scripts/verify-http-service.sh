#!/usr/bin/env bash
set -euo pipefail

: "${SERVICE_BASE_URL:?SERVICE_BASE_URL is required}"

base_url="${SERVICE_BASE_URL%/}"
[[ "$base_url" =~ ^https?://[^/]+(/.*)?$ ]] || {
  echo "SERVICE_BASE_URL must be an HTTP or HTTPS base URL" >&2
  exit 2
}

curl --fail --show-error --silent --retry 12 --retry-all-errors --retry-delay 5 \
  "$base_url/actuator/health" | grep -q '"status":"UP"'
curl --fail --show-error --silent --retry 3 --retry-all-errors --retry-delay 2 \
  -H 'Accept: text/plain' "$base_url/actuator/prometheus" | grep -q 'jvm_memory_used_bytes'
