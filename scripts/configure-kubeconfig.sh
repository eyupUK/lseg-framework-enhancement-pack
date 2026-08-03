#!/usr/bin/env bash
set -euo pipefail

: "${KUBE_CONFIG_DATA:?KUBE_CONFIG_DATA is required}"

config_dir="${KUBECONFIG_DIRECTORY:-$HOME/.kube}"
mkdir -p "$config_dir"
umask 077
printf '%s' "$KUBE_CONFIG_DATA" | base64 --decode > "$config_dir/config"
chmod 600 "$config_dir/config"
printf 'KUBECONFIG=%s/config\n' "$config_dir" >> "${GITHUB_ENV:?GITHUB_ENV is required}"
