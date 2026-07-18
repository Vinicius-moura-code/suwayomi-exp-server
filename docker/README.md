# Suwayomi Exp — Docker

Production image with **ExpUI baked in**. Data lives at `/home/suwayomi/.local/share/Tachidesk` (same layout as upstream Docker).

## Quick local build

Requires sibling checkout `../suwayomi-exp-ui`, JDK 21, Docker, Node/pnpm.

```bash
./scripts/docker/build.sh
# optional push:
./scripts/docker/build.sh --push YOURUSER/suwayomi-exp-server:dev
```

## Run (same pattern as a classic Suwayomi compose)

See [`deploy/docker-compose.example.yml`](../deploy/docker-compose.example.yml).

Defaults inside the image:

| Env | Default |
|-----|---------|
| `WEB_UI_FLAVOR` | `EXPUUI` (product name: ExpUI) |
| `WEB_UI_UPDATE_INTERVAL` | `0` |
| `KCEF_ENABLED` | `false` |

On first start (or when the baked `revision` changes), ExpUI is copied from `/opt/suwayomi-exp/webUI` into the data volume.

Compatible env vars from Suwayomi-Server-docker still work (`FLARESOLVERR_*`, `BIND_IP`, `BIND_PORT`, `DATABASE_*`, …).
