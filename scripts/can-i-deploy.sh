#!/usr/bin/env bash
set -euo pipefail

: "${PACT_BROKER_BASE_URL:?PACT_BROKER_BASE_URL is required}"
: "${PACT_BROKER_TOKEN:?PACT_BROKER_TOKEN is required}"
: "${PACTICIPANT:?PACTICIPANT is required}"
: "${APPLICATION_VERSION:?APPLICATION_VERSION is required}"
: "${TARGET_ENVIRONMENT:?TARGET_ENVIRONMENT is required}"

RETRY_COUNT="${PACT_RETRY_COUNT:-6}"
RETRY_INTERVAL="${PACT_RETRY_INTERVAL:-10}"

command -v pact-broker-cli >/dev/null 2>&1 || {
  echo "pact-broker-cli is not installed" >&2
  exit 2
}

exec pact-broker-cli can-i-deploy \
  --pacticipant "$PACTICIPANT" \
  --version "$APPLICATION_VERSION" \
  --to-environment "$TARGET_ENVIRONMENT" \
  --retry-while-unknown "$RETRY_COUNT" \
  --retry-interval "$RETRY_INTERVAL"
