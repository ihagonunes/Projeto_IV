# TASK-SPI-94 — Aplicação do RBAC no dashboard

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-94
- **História PO:** POPROJ-53
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Dashboard de Métricas, Filtros e Agregações

## Fonte original do consolidado
Gestor visualiza apenas dados de seu departamento; Admin visualiza tenant completo.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Aplicar AuthorizationScope no dashboard.

### Requisitos reconciliados
- [ ] Collaborator: próprio.
- [ ] Leader/Manager: scope autorizado.
- [ ] Executive/Admin: tenant conforme permissões.
- [ ] Frontend não substitui enforcement backend.

### Motivo
ADR-04/30.

## Área proprietária
`observability + presentation`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-13`
- `ADR-28`
- `ADR-30`

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
