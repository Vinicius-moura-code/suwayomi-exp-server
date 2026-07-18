---
name: suwayomi-roadmap
description: Use this skill when working on any improvement, bug fix, or new feature from my personal development plan for Suwayomi-Server. Always combine with suwayomi-java-kotlin skill.
---

# Suwayomi-Server — Meu Roadmap Pessoal

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

### Fase 4: Melhorias na WebUI (Cliente) — PRÓXIMA
- Botão para limpar histórico completamente
- Categorias com bloqueio de privacidade (obras sensíveis)
- Opção de ocultar obras específicas do Histórico e da aba Updates
- Marcar como lido em massa
- Melhorias gerais de usabilidade e organização

No Server: entregar APIs GraphQL primeiro. WebUI em repo separado.
Cortes sugeridos: A ocultar Histórico/Updates | B limpar histórico | C privacidade categorias | D A+B.

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
- Continuidade pós-move: ver `F:\workspace\suwayomi-exp\CONTEXT.md`

---

**Status atual**: Fase 3 pausada. Próximo: Fase 4 (escolher corte A/B/C/D).
