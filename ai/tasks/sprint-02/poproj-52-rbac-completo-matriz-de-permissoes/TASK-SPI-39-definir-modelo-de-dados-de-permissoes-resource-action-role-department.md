# TASK-SPI-39 — Definir modelo de dados de permissões (resource, action, role, department)

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-39
- **História PO:** POPROJ-52
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `complexidade-media`, `sprint-2`
- **Grupo:** RBAC Completo & Matriz de Permissões

## Fonte original do consolidado
Modelagem das tabelas de recursos, ações, papéis e departamentos.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Modelar permissões no IAM sem duplicar Department.

### Requisitos reconciliados
- [ ] Resource/action/permission/policy no IAM.
- [ ] Referenciar Department por DepartmentId.
- [ ] Não duplicar aggregate/tabela Department no IAM.

### Motivo
Department é dono de Department; IAM é dono de permissões/AuthorizationScope.

## Área proprietária
`iam`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ai/testing.md`
- `ADR-04`
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
