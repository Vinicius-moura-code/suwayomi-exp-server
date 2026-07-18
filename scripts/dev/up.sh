#!/usr/bin/env bash
# Start Postgres (and any other infra) for local development.
# Idempotent: if suwayomi-postgres is already running (even from another compose
# project), reuse it instead of failing on container name conflict.
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

CONTAINER_NAME="suwayomi-postgres"

if docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "Postgres already running (${CONTAINER_NAME}) — reusing."
elif docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "Starting existing container ${CONTAINER_NAME}..."
  docker start "${CONTAINER_NAME}" >/dev/null
else
  compose --profile infra up -d
fi

# Wait until healthy / accepting connections (best-effort)
for _ in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" pg_isready -U suwayomi -d suwayomi >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

docker ps --filter "name=^/${CONTAINER_NAME}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo "Postgres ready on localhost:5433 (user/db/password: suwayomi)"
