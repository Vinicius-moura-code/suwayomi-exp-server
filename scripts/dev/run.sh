#!/usr/bin/env bash
# Run Suwayomi-Server on the host via Gradle.
#
# Usage:
#   scripts/dev/run.sh              # Postgres (starts infra if needed)
#   scripts/dev/run.sh --h2         # file-based H2 (no Docker required)
#   scripts/dev/run.sh -- <gradle args...>
#
# Keep this terminal open while the server runs. First compile can take several minutes.
# When ready: http://localhost:4567
set -euo pipefail
# shellcheck source=scripts/dev/_common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"

USE_H2=0
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --h2)
      USE_H2=1
      shift
      ;;
    --)
      shift
      EXTRA_ARGS+=("$@")
      break
      ;;
    *)
      EXTRA_ARGS+=("$1")
      shift
      ;;
  esac
done

# Passed to Gradle CLI, then forwarded to the app JVM by server/build.gradle.kts.
# Do NOT use JAVA_TOOL_OPTIONS here — it also hits the Kotlin compiler and can OOM.
SYS_PROPS=(
  "-Dsuwayomi.tachidesk.config.server.systemTrayEnabled=false"
  "-Dsuwayomi.tachidesk.config.server.initialOpenInBrowserEnabled=false"
)

if [[ "${USE_H2}" -eq 1 ]]; then
  SYS_PROPS+=(
    "-Dsuwayomi.tachidesk.config.server.databaseType=H2"
  )
  echo "Starting server with H2..."
else
  echo "Ensuring Postgres infra is up..."
  "${DEV_SCRIPT_DIR}/up.sh"
  SYS_PROPS+=(
    "-Dsuwayomi.tachidesk.config.server.databaseType=POSTGRESQL"
    "-Dsuwayomi.tachidesk.config.server.databaseUrl=postgresql://localhost:5433/suwayomi"
    "-Dsuwayomi.tachidesk.config.server.databaseUsername=suwayomi"
    "-Dsuwayomi.tachidesk.config.server.databasePassword=suwayomi"
  )
  echo "Starting server with PostgreSQL (localhost:5433)..."
fi

# Local memory knobs (gradle.properties is gitignored). Avoid Kotlin OOM on first compile.
if [[ ! -f gradle.properties ]]; then
  cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
kotlin.daemon.jvmargs=-Xmx2048m
org.gradle.parallel=true
EOF
  echo "Created local gradle.properties (2g heap for Gradle/Kotlin)."
fi

echo "Compiling and starting… keep this window open."
echo "When up: http://localhost:4567"
exec ./gradlew :server:run --stacktrace "${SYS_PROPS[@]}" "${EXTRA_ARGS[@]}"
