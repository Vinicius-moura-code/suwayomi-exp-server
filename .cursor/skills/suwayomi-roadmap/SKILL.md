---
name: suwayomi-roadmap
description: Use this skill when working on any improvement, bug fix, or new feature from my personal development plan for Suwayomi-Server. Always combine with suwayomi-java-kotlin skill.
---

# suwayomi-exp-server — Meu Roadmap Pessoal

## Objetivo Geral
Desenvolver melhorias de qualidade, correções e novas funcionalidades no fork pessoal, com possibilidade de contribuir de volta no futuro.

## Fases do Projeto

### Fase 1: Ambiente & Infra (Concluída)
- [x] `docker-compose.yml` com profiles `infra` (Postgres) e `app` (opcional)
- [x] `Dockerfile.dev` para run containerizado via Gradle
- [x] Scripts em `scripts/dev/` (`up`, `down`, `run`, `test`, `logs`)
- [x] PostgreSQL em `localhost:5433` (credenciais `suwayomi` / `suwayomi`)
- Monitoring básico de logs/health → coberto de forma leve (`logs.sh` + healthcheck do Postgres); observabilidade real é Fase 2

### Fase 2: Monitoring e Observabilidade
**Objetivo**: Monitorar erros frequentes, armazenar recorrências e facilitar correções futuras.

- [x] MVP Error Store (fingerprint, stack, frequência, GraphQL `errorIncidents`)
- [x] Micrometer + `/api/metrics` + Compose profile `monitoring` (Prometheus + Grafana)
- [ ] Alertmanager / alertas
- [ ] Dashboard HTML interno de erros (opcional; GraphQL já cobre)

### Fase 3: Melhorias no Tracking (PAUSADA)
**Status**: pausada — trackers de terceiros em outra frente. Não priorizar até pedido explícito.

- Corrigir bugs conhecidos de tracking
- Persistência mais inteligente de progresso
- Filtros e busca
- Retry / capítulos confiáveis

### Fase 4: Melhorias no cliente — EM ANDAMENTO
- [x] **Corte A (Server + UI):** ocultar obras do Histórico/Updates (`MangaVisibility`, `updateMangaVisibility`, filtros `excludeHiddenFrom*`, toggles na manga screen)
- [ ] Corte B: limpar histórico (mutation GraphQL)
- [ ] Corte C: categorias com flag de privacidade
- Marcar como lido em massa / usabilidade geral

No Server: APIs GraphQL primeiro. UI em `F:\workspace\suwayomi-exp\suwayomi-exp-ui`.

## Princípios de Desenvolvimento

- Manter mudanças **pequenas e focadas** (ideal para PRs futuros)
- Priorizar **GraphQL** para novas funcionalidades
- Respeitar a arquitetura existente (model → impl → graphql)
- Seguir estritamente o estilo do projeto (ktlint + editorconfig)
- Documentar bem as novas features e mudanças
- Preferir soluções reutilizáveis e bem testadas

## Como Usar Esta Skill

Sempre que eu mencionar qualquer task deste roadmap:
- Consulte esta skill + `suwayomi-java-kotlin`
- Sugira implementação seguindo as camadas corretas
- Mantenha o escopo pequeno
- Inclua testes quando aplicável
- Pense em como a mudança pode ser documentada
- Continuidade do hub: `F:\workspace\suwayomi-exp\CONTEXT.md` (+ `AGENTS.md` do hub)

---

**Status atual**: Fase 4 corte A (Server + UI) feito. Próximo: corte B/C. Hub em `suwayomi-exp`.
