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
scripts/dev/up.sh          # sobe Postgres
scripts/dev/run.sh         # server no host + Postgres (localhost:5433)
scripts/dev/run.sh --h2    # server com H2 (sem Docker)
scripts/dev/test.sh        # ./gradlew :server:test
scripts/dev/logs.sh        # logs do Compose
scripts/dev/down.sh        # derruba a stack
```

App 100% no container (opcional): `docker compose --profile app up --build`

**Importante:** no fluxo Gradle, use `-Dsuwayomi.tachidesk.config.server.*` (ou `scripts/dev/run.sh`).
Vars `DATABASE_*` / `BIND_*` são do entrypoint da imagem oficial, não do `:server:run`.
URL do Postgres **sem** prefixo `jdbc:` (o server adiciona).

### Fase 2: Monitoring e Observabilidade
- Implementar mecanismos para monitorar erros frequentes
- Armazenar e analisar recorrência de erros
- Criar base para futuras correções automáticas ou alerts

### Fase 3: Melhorias no Tracking
- Corrigir problemas existentes de tracking
- Adicionar persistência inteligente
- Melhorar filtros e busca de obras (principal dor atual)

### Fase 4: Melhorias na WebUI
- Botão para limpar histórico
- Categorias com bloqueio de privacidade/segurança
- Opção de ocultar obras do histórico e da aba "Updates"
- Outras melhorias de usabilidade

## Regras Importantes para o Agent
- Sempre respeitar a arquitetura atual do projeto (GraphQL first, layers bem definidas)
- Manter as mudanças pequenas e focadas
- Seguir o estilo de código existente (use a skill `suwayomi-java-kotlin`)
- Preferir soluções que possam virar PRs limpos no futuro
- Documentar bem as mudanças

---

**Quando eu falar de uma task, use o contexto completo deste plano + a skill suwayomi-java-kotlin.**
