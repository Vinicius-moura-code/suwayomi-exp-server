# Local / CI helper: build ExpUI + server jar, then Docker image.
#
# Usage (from repo root, with sibling ../suwayomi-exp-ui):
#   ./scripts/docker/build.sh
#   ./scripts/docker/build.sh --push myuser/suwayomi-exp-server:dev
#
# Env:
#   UI_ROOT          path to suwayomi-exp-ui (default: ../suwayomi-exp-ui)
#   IMAGE            image name:tag (default: suwayomi-exp-server:local)
#   ProductName      Gradle product name (default: Suwayomi-Exp-Server)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
UI_ROOT="${UI_ROOT:-${SERVER_ROOT}/../suwayomi-exp-ui}"
IMAGE="${IMAGE:-suwayomi-exp-server:local}"
PRODUCT_NAME="${ProductName:-Suwayomi-Exp-Server}"
PUSH=0
PUSH_IMAGE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --push)
      PUSH=1
      if [[ $# -ge 2 && "$2" != --* ]]; then
        PUSH_IMAGE="$2"
        shift
      fi
      shift
      ;;
    -h|--help)
      sed -n '2,16p' "$0"
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
  echo "Set UI_ROOT=... or place suwayomi-exp-ui as a sibling directory." >&2
  exit 1
fi

echo "==> Building ExpUI from ${UI_ROOT}"
(
  cd "${UI_ROOT}"
  export CI=true
  if [[ -x node_modules/.bin/next ]]; then
    node_modules/.bin/next build
  else
    corepack enable >/dev/null 2>&1 || true
    pnpm install --frozen-lockfile --ignore-scripts
    pnpm exec next build
  fi
)

BUILD_DIR="${UI_ROOT}/build"
if [[ ! -f "${BUILD_DIR}/index.html" ]]; then
  echo "Missing ${BUILD_DIR}/index.html" >&2
  exit 1
fi

REV="r$(cd "${UI_ROOT}" && git rev-list HEAD --count 2>/dev/null || echo 0)"
echo "${REV}" > "${BUILD_DIR}/revision"

EXPUI_DEST="${SERVER_ROOT}/docker/expui"
echo "==> Copying ExpUI → ${EXPUI_DEST} (${REV})"
rm -rf "${EXPUI_DEST}"
mkdir -p "${EXPUI_DEST}"
cp -a "${BUILD_DIR}/." "${EXPUI_DEST}/"

echo "==> Building server jar (ProductName=${PRODUCT_NAME})"
(
  cd "${SERVER_ROOT}"
  export ProductName="${PRODUCT_NAME}"
  ./gradlew :server:shadowJar --no-daemon
)

JAR="$(ls -1 "${SERVER_ROOT}/server/build/${PRODUCT_NAME}"-*.jar 2>/dev/null | head -n1 || true)"
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  # fallback: any jar in build that is not -plain
  JAR="$(ls -1 "${SERVER_ROOT}/server/build/"*.jar 2>/dev/null | grep -v plain | head -n1 || true)"
fi
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "Server jar not found under server/build/" >&2
  exit 1
fi

cp -f "${JAR}" "${SERVER_ROOT}/docker/server.jar"
echo "    jar: ${JAR}"

VERSION="$(basename "${JAR}" .jar)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
VCS_REF="$(cd "${SERVER_ROOT}" && git rev-parse --short HEAD 2>/dev/null || echo unknown)"

echo "==> docker build ${IMAGE}"
docker build \
  -f "${SERVER_ROOT}/docker/Dockerfile" \
  --build-arg "BUILD_DATE=${BUILD_DATE}" \
  --build-arg "VERSION=${VERSION}" \
  --build-arg "VCS_REF=${VCS_REF}" \
  -t "${IMAGE}" \
  "${SERVER_ROOT}"

echo "Built ${IMAGE}"

if [[ "${PUSH}" -eq 1 ]]; then
  TARGET="${PUSH_IMAGE:-${IMAGE}}"
  if [[ "${TARGET}" != "${IMAGE}" ]]; then
    docker tag "${IMAGE}" "${TARGET}"
  fi
  echo "==> docker push ${TARGET}"
  docker push "${TARGET}"
fi
