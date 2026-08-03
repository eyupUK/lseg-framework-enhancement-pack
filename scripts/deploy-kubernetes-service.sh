#!/usr/bin/env bash
set -euo pipefail

: "${K8S_NAMESPACE:?K8S_NAMESPACE is required}"
: "${K8S_MANIFEST:?K8S_MANIFEST is required}"
: "${SERVICE_NAME:?SERVICE_NAME is required}"
: "${SERVICE_IMAGE:?SERVICE_IMAGE is required}"

command -v kubectl >/dev/null 2>&1 || {
  echo "kubectl is required to deploy a service" >&2
  exit 2
}

[[ -f "$K8S_MANIFEST" ]] || {
  echo "Kubernetes manifest not found: $K8S_MANIFEST" >&2
  exit 2
}

kubectl -n "$K8S_NAMESPACE" apply -f "$K8S_MANIFEST"
kubectl -n "$K8S_NAMESPACE" set image "deployment/$SERVICE_NAME" "$SERVICE_NAME=$SERVICE_IMAGE"
kubectl -n "$K8S_NAMESPACE" rollout status "deployment/$SERVICE_NAME" --timeout="${ROLLOUT_TIMEOUT:-180s}"
