# TASK-SPI-84 — Endpoint de consulta do modelo permitido para o usuário autenticado

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-84
- **História PO:** POPROJ-21
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Fonte original do consolidado
Retorna modelo disponível considerando cargo e departamento do solicitante.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Consultar modelo aplicável combinando autorização IAM + ModelPolicy.

### Requisitos reconciliados
- [ ] IAM decide autorização.
- [ ] Department.ModelPolicy decide modelos permitidos.
- [ ] Sem segunda política por HierarchyLevel.
- [ ] Respeitar tenant/scope.

### Motivo
Ownerships separados.

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
