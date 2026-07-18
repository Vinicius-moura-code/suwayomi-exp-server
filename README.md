# Suwayomi Exp Server

Open-source manga reader **server** — a personal hard fork of [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server), maintained independently.

Runs Mihon (Tachiyomi) extensions, exposes GraphQL/REST APIs, and serves **[ExpUI](https://github.com/Vinicius-moura-code/suwayomi-exp-ui)** as the default web interface (flavor `ExpUI`). Not affiliated with the upstream Suwayomi project.

## Credits

This project is based on work by many people. Thank you.

| Project | Role |
|---------|------|
| [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) | Upstream server this fork started from |
| [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server) | Spiritual predecessor; many ideas and groundwork |
| [AndroidCompat](https://github.com/null-dev) ([@null-dev](https://github.com/null-dev)) | Compatibility layer — Apache-2.0, Copyright 2019 Andy Bao and contributors |
| [Mihon (Tachiyomi)](https://github.com/mihonapp/mihon) | Adopted portions — Apache-2.0, Copyright 2015 Javier Tomás |

Apache License 2.0: http://www.apache.org/licenses/LICENSE-2.0  
Changes to those codebases, and the rest of this project, are under **MPL-2.0**.

## Features

Inherited from Suwayomi-Server (extensions, library, downloads, backups, tracking, FlareSolverr, OPDS, …), plus Exp-focused work such as:

- Default UI flavor **ExpUI** (local `webUI/` folder; no GitHub auto-update)
- Dev stack (Compose Postgres, scripts under `scripts/dev/`)
- Observability (Error Store via GraphQL, Prometheus metrics)
- Library privacy helpers (e.g. hide titles from History / Updates)

See [`AGENTS.md`](./AGENTS.md) for the current roadmap.

## Related

| Repo | What |
|------|------|
| [suwayomi-exp-ui](https://github.com/Vinicius-moura-code/suwayomi-exp-ui) | ExpUI — Next.js client (official UI for this fork) |
| [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) | Upstream (reference / sync source) |

Configuration notes live under [`docs/`](./docs/). Upstream wiki remains useful for many server concepts: [Suwayomi-Server wiki](https://github.com/Suwayomi/Suwayomi-Server/wiki).

## Docker (production)

Image ships with **ExpUI baked in** (`WEB_UI_FLAVOR=ExpUI`). Layout matches classic Suwayomi Docker (`/home/suwayomi/.local/share/Tachidesk`).

```bash
# Local image (needs sibling ../suwayomi-exp-ui)
./scripts/docker/build.sh

# Example stack (edit YOURUSER)
cp deploy/docker-compose.example.yml docker-compose.prod.yml
```

CI: tag `v*` (or Actions → **Docker Publish**) pushes to Docker Hub as `YOURUSER/suwayomi-exp-server` (`stable` / `latest` / semver).

Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`. Details: [`docker/README.md`](./docker/README.md).

## Development

Requirements: **JDK 21+**, Docker (optional, for Postgres).

```bash
scripts/dev/up.sh                 # Postgres on localhost:5433
scripts/dev/run.sh                # server → http://localhost:4567 (ExpUI flavor)
scripts/dev/install-ui.sh         # build ExpUI → data webUI/
scripts/dev/run.sh --h2           # H2 only (no Docker)
scripts/dev/down.sh
```

| Service | URL |
|---------|-----|
| Server + ExpUI (after `install-ui`) | http://localhost:4567 |
| GraphQL | http://localhost:4567/api/graphql |
| Metrics | http://localhost:4567/api/metrics |

UI hot-reload during development: run ExpUI separately on http://localhost:3000.

## License

```
Copyright (C) Contributors to the Suwayomi project
Copyright (C) Contributors to Suwayomi Exp

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
```

Full text: [`LICENSE`](./LICENSE).

## Disclaimer

This software is not affiliated with any content providers or with the upstream Suwayomi organization. You are responsible for how you use it and for complying with applicable laws.
