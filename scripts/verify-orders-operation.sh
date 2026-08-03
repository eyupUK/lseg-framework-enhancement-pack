#!/usr/bin/env bash
set -euo pipefail

: "${ORDERS_BASE_URL:?ORDERS_BASE_URL is required}"

base_url="${ORDERS_BASE_URL%/}"
correlation_id="release-smoke-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

status="$(curl --show-error --silent --output "$response_file" --write-out '%{http_code}' \
  --retry 3 --retry-all-errors --retry-delay 2 \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $correlation_id" \
  --data '{"userId":1,"item":"release-smoke","amount":1.00}' \
  "$base_url/orders/new")"

[[ "$status" == "200" ]] || {
  echo "Orders smoke operation returned HTTP $status" >&2
  cat "$response_file" >&2
  exit 1
}

grep -Eq '"id"[[:space:]]*:[[:space:]]*"?[^",}]+' "$response_file" || {
  echo "Orders smoke operation did not return an order id" >&2
  cat "$response_file" >&2
  exit 1
}
