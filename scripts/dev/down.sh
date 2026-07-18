#!/usr/bin/env bash
# Stop the local docker compose stack (infra + app + monitoring).
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

compose --profile infra --profile app --profile monitoring down "$@"
