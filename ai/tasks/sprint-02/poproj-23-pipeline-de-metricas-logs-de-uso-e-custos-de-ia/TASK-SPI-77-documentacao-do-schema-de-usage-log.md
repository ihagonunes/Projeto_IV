# TASK-SPI-77 — Documentação do schema de usage log

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-77
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Documentação publicada e vinculada aos ADRs e arquitetura da Epic.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

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
