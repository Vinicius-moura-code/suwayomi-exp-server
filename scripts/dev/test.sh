#!/usr/bin/env bash
# Run server tests. Extra args are forwarded to Gradle (e.g. --tests pkg.Class).
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

exec ./gradlew :server:test --stacktrace "$@"
