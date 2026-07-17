#!/usr/bin/env bash
# Start Postgres (and any other infra) for local development.
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

compose --profile infra up -d
compose --profile infra ps
echo "Postgres ready on localhost:5433 (user/db/password: suwayomi)"
