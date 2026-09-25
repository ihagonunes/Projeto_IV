# TASK-SPI-82 — Endpoint de configuração de departamentos por modelo

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-82
- **História PO:** POPROJ-21
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Fonte original do consolidado
Vinculação de departamentos aos modelos permitidos com validação de tenant.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Configurar ModelPolicy do Department, não Departments dentro de Model.

### Requisitos reconciliados
- [ ] Atualizar allowedModelIds/defaultModelId.
- [ ] Validar ModelIds por API pública do Gateway.
- [ ] Sem associação JPA/FK Model↔Department.

### Motivo
ADR-07/29.

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
