#!/usr/bin/env bash
# Follow docker compose logs (infra + app profiles).
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

compose --profile infra --profile app logs -f "$@"
