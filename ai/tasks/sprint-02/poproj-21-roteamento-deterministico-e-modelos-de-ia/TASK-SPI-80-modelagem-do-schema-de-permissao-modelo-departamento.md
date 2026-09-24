# TASK-SPI-80 — Modelagem do schema de permissão modelo × departamento

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-80
- **História PO:** POPROJ-21
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Fonte original do consolidado
Relação N:N entre modelos permitidos e departamentos da organização.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Modelar ModelPolicy no Department por IDs, sem @ManyToMany/FK cross-module.

### Requisitos reconciliados
- [ ] allowedModelIds[] + defaultModelId.
- [ ] Default pertence aos permitidos.
- [ ] Sem @ManyToMany Department↔Model.
- [ ] Sem FK SQL cross-module.

### Motivo
ADR-07/08/29.

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
