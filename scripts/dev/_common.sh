#!/usr/bin/env bash
# Shared helpers for scripts/dev/*.sh
set -euo pipefail

DEV_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DEV_SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

compose() {
  docker compose "$@"
}
