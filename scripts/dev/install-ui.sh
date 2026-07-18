#!/usr/bin/env bash
# Build suwayomi-exp-ui and install into the Server data webUI directory (ExpUI flavor).
#
# Usage (from hub or server repo):
#   ./scripts/dev/install-ui.sh
#   ./scripts/dev/install-ui.sh --skip-build   # only copy existing build/
#
# Default target (Windows): %LOCALAPPDATA%/Tachidesk/webUI
# Override: SUWAYOMI_DATA_ROOT=/path/to/data ./scripts/dev/install-ui.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
HUB_ROOT="$(cd "${SERVER_ROOT}/.." && pwd)"
UI_ROOT="${HUB_ROOT}/suwayomi-exp-ui"

SKIP_BUILD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1; shift ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${UI_ROOT}/package.json" ]]; then
  echo "UI repo not found at ${UI_ROOT}" >&2
  exit 1
fi

if [[ -n "${SUWAYOMI_DATA_ROOT:-}" ]]; then
  DATA_ROOT="${SUWAYOMI_DATA_ROOT}"
elif [[ -n "${LOCALAPPDATA:-}" ]]; then
  DATA_ROOT="${LOCALAPPDATA}/Tachidesk"
elif [[ -d "${HOME}/.local/share/Tachidesk" ]]; then
  DATA_ROOT="${HOME}/.local/share/Tachidesk"
else
  DATA_ROOT="${HOME}/.local/share/Tachidesk"
fi

WEBUI_DIR="${DATA_ROOT}/webUI"
BUILD_DIR="${UI_ROOT}/build"

echo "UI:   ${UI_ROOT}"
echo "Data: ${DATA_ROOT}"
echo "Dest: ${WEBUI_DIR}"

if [[ "${SKIP_BUILD}" -eq 0 ]]; then
  echo "Building UI (static export → build/)…"
  (
    cd "${UI_ROOT}"
    export CI=true
    if [[ -x node_modules/.bin/next ]]; then
      node_modules/.bin/next build
    else
      pnpm install --frozen-lockfile --ignore-scripts
      pnpm exec next build
    fi
  )
fi

if [[ ! -f "${BUILD_DIR}/index.html" ]]; then
  echo "Missing ${BUILD_DIR}/index.html — run a full build first." >&2
  exit 1
fi

# revision file (About / version tag)
REV="r$(cd "${UI_ROOT}" && git rev-list HEAD --count 2>/dev/null || echo 0)"
echo "${REV}" > "${BUILD_DIR}/revision"
echo "revision=${REV}"

mkdir -p "${WEBUI_DIR}"
# Replace contents but keep the directory
find "${WEBUI_DIR}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -a "${BUILD_DIR}/." "${WEBUI_DIR}/"

echo ""
echo "Installed ExpUI into ${WEBUI_DIR}"
echo "Ensure server.webUIFlavor = \"ExpUI\" (run.sh sets this for dev)."
echo "Restart the server, then open http://localhost:4567"
