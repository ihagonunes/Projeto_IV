# TASK-SPI-74 — Tratamento de falhas na captura de métricas

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-74
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Garantia de registro de log de falhas (ex: timeouts de provedores) sem descartar erro do cliente.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Tratar falhas pré-resposta com logging técnico estruturado.

### Requisitos reconciliados
- [ ] Registrar timeout/erro técnico.
- [ ] Retornar exceção abstrata.
- [ ] Sem AIRequestFailed no MVP.
- [ ] Sem UsageRecord de falha pré-resposta.

### Motivo
ADR-12.

## Área proprietária
`observability + aigateway`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-13`
- `ADR-26`
- `ADR-29`

## Regras transversais
- Cross-module somente via `module.api.*`.
- Sem associação JPA/FK SQL cross-module.
- TenantId vem do contexto autenticado.
- Persistência muda somente com Flyway.
- PostgreSQL é o único banco suportado.
- Frontend nunca substitui autorização backend.
- Mudança estrutural exige ADR.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
