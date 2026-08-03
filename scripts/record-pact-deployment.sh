#!/usr/bin/env bash
set -euo pipefail

: "${PACT_BROKER_BASE_URL:?PACT_BROKER_BASE_URL is required}"
: "${PACT_BROKER_TOKEN:?PACT_BROKER_TOKEN is required}"
: "${PACTICIPANT:?PACTICIPANT is required}"
: "${APPLICATION_VERSION:?APPLICATION_VERSION is required}"
: "${TARGET_ENVIRONMENT:?TARGET_ENVIRONMENT is required}"

command -v pact-broker-cli >/dev/null 2>&1 || {
  echo "pact-broker-cli is required to record a deployment" >&2
  exit 2
}

exec pact-broker-cli record-deployment \
  --pacticipant "$PACTICIPANT" \
  --version "$APPLICATION_VERSION" \
  --environment "$TARGET_ENVIRONMENT"
