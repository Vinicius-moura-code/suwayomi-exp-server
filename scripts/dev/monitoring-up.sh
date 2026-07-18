#!/usr/bin/env bash
# Start Prometheus + Grafana for local metrics (scrapes host :4567/api/metrics).
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

compose --profile monitoring up -d
compose --profile monitoring ps
echo "Prometheus: http://localhost:9090"
echo "Grafana:    http://localhost:3000  (admin/admin, anonymous Viewer enabled)"
echo "Scrape:     http://host.docker.internal:4567/api/metrics  (app must be running)"
