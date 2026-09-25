# TASK-SPI-79 — Modelagem do schema de Model

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-79
- **História PO:** POPROJ-21
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Fonte original do consolidado
Schema de catálogo de modelos associado por tenant.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Modelar Model no AI Gateway referenciando ProviderId.

### Requisitos reconciliados
- [ ] Model pertence ao aigateway.
- [ ] Estado AVAILABLE/DISABLED.
- [ ] Referência por ProviderId.
- [ ] Sem acoplamento a Department entity.

### Motivo
ADR-09.

## Área proprietária
`aigateway + department`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-07`
- `ADR-08`
- `ADR-09`
- `ADR-11`
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
