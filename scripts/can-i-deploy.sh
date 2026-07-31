#!/usr/bin/env bash
set -euo pipefail

: "${PACT_BROKER_BASE_URL:?PACT_BROKER_BASE_URL is required}"
: "${PACT_BROKER_TOKEN:?PACT_BROKER_TOKEN is required}"
: "${PACTICIPANT:?PACTICIPANT is required}"
: "${APPLICATION_VERSION:?APPLICATION_VERSION is required}"
: "${TARGET_ENVIRONMENT:?TARGET_ENVIRONMENT is required}"

command -v pact-broker >/dev/null 2>&1 || {
  echo "pact-broker CLI is not installed" >&2
  exit 2
}

pact-broker can-i-deploy \
  --broker-base-url "$PACT_BROKER_BASE_URL" \
  --broker-token "$PACT_BROKER_TOKEN" \
  --pacticipant "$PACTICIPANT" \
  --version "$APPLICATION_VERSION" \
  --to-environment "$TARGET_ENVIRONMENT" \
  --retry-while-unknown 6 \
  --retry-interval 10
