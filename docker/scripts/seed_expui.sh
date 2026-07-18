#!/bin/sh
# Copy baked-in ExpUI into the data volume (first boot or image upgrade).
set -e

SRC="/opt/suwayomi-exp/webUI"
DEST="/home/suwayomi/.local/share/Tachidesk/webUI"

if [ ! -f "${SRC}/index.html" ]; then
  echo "WARNING: baked ExpUI missing at ${SRC} — UI will be empty until installed"
  exit 0
fi

mkdir -p "${DEST}"

should_sync=0
if [ ! -f "${DEST}/index.html" ]; then
  should_sync=1
  echo "Seeding ExpUI into data volume…"
elif [ -f "${SRC}/revision" ] && [ -f "${DEST}/revision" ]; then
  src_rev="$(tr -d '\r\n' < "${SRC}/revision")"
  dest_rev="$(tr -d '\r\n' < "${DEST}/revision")"
  if [ "${src_rev}" != "${dest_rev}" ]; then
    should_sync=1
    echo "Updating ExpUI ${dest_rev} → ${src_rev}…"
  fi
elif [ -f "${SRC}/revision" ] && [ ! -f "${DEST}/revision" ]; then
  should_sync=1
  echo "Updating ExpUI (no revision in volume)…"
fi

if [ "${should_sync}" -eq 1 ]; then
  # Replace directory contents, keep the folder itself (volume mount friendly)
  find "${DEST}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -a "${SRC}/." "${DEST}/"
  echo "ExpUI ready at ${DEST}"
fi
