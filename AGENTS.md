# Suwayomi-Server - Meu Plano de Desenvolvimento Pessoal

## Visão Geral
Estou fazendo um fork do Suwayomi-Server para:
- Corrigir bugs
- Adicionar melhorias de qualidade
- Implementar funcionalidades úteis para uso pessoal e possível contribuição futura

## Prioridades Atuais

### Fase 1: Ambiente de Desenvolvimento
- [x] Corrigir `docker-compose.yml` + `Dockerfile.dev` (profiles infra/app, Postgres, cache Gradle)
- [x] Criar scripts de dev (`scripts/dev/`)
- [x] PostgreSQL fácil via Compose (porta host `5433`)

#### Como usar (dia a dia)

```bash
scripts/dev/up.sh                 # sobe Postgres
scripts/dev/run.sh                # server no host + Postgres (localhost:5433); Flavor ExpUI
scripts/dev/run.sh --h2           # server com H2 (sem Docker)
scripts/dev/install-ui.sh         # build suwayomi-exp-ui → data webUI/ (ExpUI)
scripts/dev/monitoring-up.sh      # Prometheus + Grafana
scripts/dev/test.sh               # ./gradlew :server:test
scripts/dev/logs.sh               # logs do Compose
scripts/dev/down.sh               # derruba a stack (infra/app/monitoring)
scripts/docker/build.sh           # imagem prod (ExpUI embutido) → Docker local
```

App 100% no container (opcional): `docker compose --profile app up --build`

**UI oficial deste fork:** Flavor **`ExpUI`** (default). Serve `%LOCALAPPDATA%/Tachidesk/webUI` (ou `~/.local/share/Tachidesk/webUI`); sem download GitHub. Dev hot-reload da UI: repo `suwayomi-exp-ui` em `:3000`.

**Imagem Docker de produção:** `docker/Dockerfile` + `.github/workflows/docker-publish.yml` (Docker Hub). ExpUI vai embutido em `/opt/suwayomi-exp/webUI` e é copiado para o volume na subida. Exemplo: `deploy/docker-compose.example.yml`.

**Importante:** no fluxo Gradle, use `-Dsuwayomi.tachidesk.config.server.*` (ou `scripts/dev/run.sh`).
Vars `DATABASE_*` / `BIND_*` / `FLARESOLVERR_*` são do **entrypoint Docker** (imagem prod), não do `:server:run`.
URL do Postgres **sem** prefixo `jdbc:` (o server adiciona).

### Fase 2: Monitoring e Observabilidade
- [x] MVP Error Store: fingerprint + frequência no DB + GraphQL (`errorIncidents`)
- [x] Micrometer + Prometheus scrape (`GET /api/metrics`) + Grafana no Compose
- [ ] Alertmanager / alertas (corte seguinte)
- [ ] Dashboard HTML de erros (Error Store segue via GraphQL)

#### Error Store (MVP)

Erros de Javalin (exceto auth) e GraphQL são agregados por fingerprint.

No GraphiQL (`http://localhost:4567/api/graphql`):

```graphql
query {
  errorIncidents(limit: 20, orderBy: OCCURRENCE_COUNT) {
    id
    exceptionClass
    message
    source
    occurrenceCount
    lastSeenAt
    context
  }
}
```

Mutations: `deleteErrorIncident`, `clearErrorIncidents` (requerem auth).

#### Métricas (MVP)

Com o server rodando:

| O quê | URL |
|--------|-----|
| Prometheus metrics | http://localhost:4567/api/metrics |
| Prometheus UI | http://localhost:9090 |
| Grafana | http://localhost:3000 (`admin`/`admin`; anonymous Viewer) |

`/api/metrics` fica **sem auth** de propósito (scrape local via Docker). Não expor em produção sem rede/auth.

### Fase 3: Melhorias no Tracking
- **Status: PAUSADA** (trackers de terceiros — outra frente)
- Corrigir problemas existentes de tracking
- Adicionar persistência inteligente
- Melhorar filtros e busca de obras

### Fase 4: Melhorias na WebUI — **EM ANDAMENTO (corte A)**
- [x] **Corte A (Server + UI):** ocultar obras do Histórico/Updates
  - Meta: `suwayomi.hideFromHistory` / `suwayomi.hideFromUpdates`
  - Campos GraphQL: `Manga.hideFromHistory` / `hideFromUpdates`
  - Mutation: `updateMangaVisibility`
  - Filtros: `chapters(filter: { excludeHiddenFromHistory / excludeHiddenFromUpdates })`
  - UI: filtros em History/Updates + toggles no menu da manga
- [ ] Botão para limpar histórico (corte B)
- [ ] Categorias com bloqueio de privacidade/segurança (corte C)

Neste repo (Server): APIs GraphQL primeiro.
UI: `F:\workspace\suwayomi-exp\suwayomi-exp-ui`.

Hub: `F:\workspace\suwayomi-exp\CONTEXT.md` + `AGENTS.md`

#### Ocultar Histórico/Updates (corte A)

```graphql
mutation {
  updateMangaVisibility(
    input: { mangaId: 1, hideFromHistory: true, hideFromUpdates: true }
  ) {
    manga { id hideFromHistory hideFromUpdates }
  }
}

query {
  chapters(
    filter: {
      lastReadAt: { isNull: false, notEqualToAll: ["0"] }
      excludeHiddenFromHistory: true
    }
    order: [{ by: LAST_READ_AT, byType: DESC }]
    first: 50
  ) {
    nodes { id mangaId }
  }
}
```

## Regras Importantes para o Agent
- Sempre respeitar a arquitetura atual do projeto (GraphQL first, layers bem definidas)
- Manter as mudanças pequenas e focadas
- Seguir o estilo de código existente (use a skill `suwayomi-java-kotlin`)
- Preferir soluções que possam virar PRs limpos no futuro
- Documentar bem as mudanças

---

**Quando eu falar de uma task, use o contexto completo deste plano + a skill suwayomi-java-kotlin.**
