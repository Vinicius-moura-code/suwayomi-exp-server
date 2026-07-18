---
name: suwayomi-java-kotlin
description: Develop Java/Kotlin features in Suwayomi-Server using Javalin, GraphQL-Kotlin, Exposed, and AndroidCompat. Use when editing server Kotlin/Java code, GraphQL API, REST controllers, database tables/migrations, extensions, or running Gradle build/test/run tasks in this repo.
---

# Suwayomi-Server Java/Kotlin Development

## Stack and constraints

- **Language**: Kotlin 2.x, JVM target **21**
- **HTTP**: Javalin (`server/src/main/kotlin/suwayomi/tachidesk/server/JavalinSetup.kt`)
- **Primary API**: GraphQL via graphql-kotlin (`/api/graphql`); GraphiQL in browser at same path
- **Legacy API**: REST `/api/v1` (soon deprecated) — prefer GraphQL for new work
- **ORM**: Exposed (`org.jetbrains.exposed.v1.*`); H2 or Postgres
- **DI**: Injekt (`injectLazy`) for Tachiyomi-compat paths; Koin also present
- **Lint**: ktlint (runs as dependency of Kotlin compile); respect `.editorconfig`
- **License header**: MPL-2.0 copyright block on new source files (match neighbors)

Project vision (from hub/AGENTS): prefer Mihon/Tachiyomi-aligned features; keep PRs small (one meaningful change).

## Modules

| Module | Role |
|--------|------|
| `server` | Main app: GraphQL, REST, manga domain, DB |
| `server/i18n` | Strings (moko) |
| `server/server-config` | Shared config/GraphQL setting types |
| `server/server-config-generate` | Config generation |
| `AndroidCompat` | Android stubs so Mihon extensions run on JVM |

Catalog/versions: `gradle/libs.versions.toml`. Root wiring: `settings.gradle.kts`, `build.gradle.kts`.

## Package layout (`suwayomi.tachidesk`)

```
manga/          # domain: controller, impl, model/{table,dataclass}
global/         # cross-cutting REST + impl
graphql/        # queries, mutations, subscriptions, types, dataLoaders, server
opds/           # OPDS feed API
server/         # bootstrap, DB migrations, settings, user auth
eu.kanade...    # Tachiyomi/Mihon-compat source & network code
```

Layering for domain features:

1. **`model/table`** — Exposed `IntIdTable` / helpers (`truncatingVarchar`, `unlimitedVarchar`)
2. **`model/dataclass`** — API/DTO shapes for REST
3. **`impl`** — business logic (objects/classes called by GraphQL and REST)
4. **`graphql/`** — queries/mutations/types/dataLoaders (preferred surface)
5. **`controller`** — REST handlers only when needed (legacy)

## Commands

```bash
# Preferred local loop (Postgres via Compose)
scripts/dev/up.sh
scripts/dev/run.sh                 # or: scripts/dev/run.sh --h2
scripts/dev/monitoring-up.sh       # Prometheus :9090 + Grafana :3000
scripts/dev/test.sh
scripts/dev/logs.sh
scripts/dev/down.sh

./gradlew :server:run --stacktrace          # raw Gradle (H2 unless -D set)
./gradlew :server:test                      # all tests
./gradlew :server:test --tests pkg.Class    # one class
./gradlew :server:shadowJar                 # server jar (no WebUI if WebUI.zip absent)
./gradlew server:downloadWebUI server:shadowJar   # jar + WebUI
./gradlew :server:ktlintCheck               # lint without format
```

JDK 21+ (Temurin/Zulu preferred). Dev Compose: see `docker-compose.yml` / `AGENTS.md`.

## Adding a GraphQL feature

1. Put logic in `manga/impl` (or `global/impl`), not only in the mutation class.
2. Add/extend type in `graphql/types/`.
3. Add query in `graphql/queries/` and/or mutation in `graphql/mutations/`.
4. Use `@RequireAuth` on operations that need a user.
5. Register new top-level classes in `graphql/server/TachideskGraphQLSchema.kt` (`TopLevelObject(...)`).
6. For N+1 fields, add a DataLoader under `graphql/dataLoaders/` and register in `TachideskDataLoaderRegistryFactory`.
7. List queries: follow existing filter/`OrderBy`/cursor patterns (`graphql/server/primitives`, `graphql/queries/filter`).
8. Mutations: Relay-style `*Input` / `*Payload` with optional `clientMutationId`; use `JavalinSetup.future` for async work returning `CompletableFuture`.

Prefer matching a nearby query/mutation (e.g. `MangaQuery` / `MangaMutation`) over inventing a new style.

## Adding REST (avoid unless required)

Use `handler(...)` + `pathParam` / `queryParam` / `withOperation` OpenAPI docs, as in `manga/controller/MangaController.kt`. Call `requireUser()` (or basic fallback) from the user attribute. Prefer calling shared `impl` code already used by GraphQL.

## Database changes

1. Update Exposed table in `model/table/`.
2. Add migration `M00NN_Description.kt` under `server/.../server/database/migration/` — next number after the latest `M00xx_*`.
3. Prefer helpers from `de.neonew.exposed.migrations.helpers` (e.g. `AddColumnMigration`) when they fit; otherwise follow recent migration classes.
4. Suppress `ClassName` / `unused` like existing migrations.
5. Keep SQL types aligned with column definitions (varchar lengths, nullability).

## Style rules specific to this repo

- Indent 4 spaces; trailing commas allowed; **no star imports** (editorconfig sets import thresholds very high).
- Exposed imports are `org.jetbrains.exposed.v1.*` (not the old package).
- Use `transaction { }` for DB work.
- Long/Duration/Cursor/Upload have custom GraphQL scalars — do not change encoding lightly (JS clients depend on string Longs).
- Do not expand REST for new features; GraphQL first.
- Do not add features that contradict Mihon alignment without explicit user request.
- Keep changes scoped: one feature/fix per PR-sized change set.

## Tests

- Live under `server/src/test/kotlin/suwayomi/tachidesk/` (also `graphql/`, `manga/`, helpers in `test/`).
- Use existing test utilities in `suwayomi.tachidesk.test` when present.
- Run `:server:test` (or a focused `--tests`) after behavioral changes.

## Gotchas

- **AndroidCompat / extensions**: extension APKs run via dex2jar + android stubs; changes under `eu.kanade` affect extension compatibility — be conservative.
- **OkHttp major version** is locked by Tachiyomi extensions — do not bump casually.
- **Jackson version** is tied to Javalin.
- Config/settings types may live in `server-config`; regenerate/config docs if you touch generated surfaces.
- `sourceReference` column maps manga source id (field name `source` is reserved on Exposed id tables).
- WebUI is a separate repo (`suwayomi-exp-ui`); this server only serves/bundles it.

## Contribution checklist

- [ ] Logic in `impl` (shared), GraphQL wired and registered
- [ ] Migration if schema changed
- [ ] Auth directive / `requireUser` where needed
- [ ] ktlint-clean Kotlin; MPL header on new files
- [ ] `:server:test` for touched behavior
- [ ] Change set small and focused
