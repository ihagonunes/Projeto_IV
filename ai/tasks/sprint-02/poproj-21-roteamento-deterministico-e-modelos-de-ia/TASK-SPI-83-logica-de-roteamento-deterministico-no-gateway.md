# TASK-SPI-83 — Lógica de roteamento determinístico no Gateway

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-83
- **História PO:** POPROJ-21
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Fonte original do consolidado
Roteamento baseado estritamente na regra explícita configurada, sem escolha heurística.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Implementar routing determinístico por ModelPolicy.

### Requisitos reconciliados
- [ ] requestedModelId presente → validar allowedModelIds.
- [ ] Ausente → usar defaultModelId.
- [ ] Validar Model AVAILABLE.
- [ ] Validar Provider ACTIVE.
- [ ] Sem fallback/heurística/custo/latência.

### Motivo
ADR-07/08/11.

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
