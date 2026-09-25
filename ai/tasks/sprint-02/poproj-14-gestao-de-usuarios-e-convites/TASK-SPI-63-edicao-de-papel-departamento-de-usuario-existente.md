# TASK-SPI-63 — Edição de papel/departamento de usuário existente

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-63
- **História PO:** POPROJ-14
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Gestão de Usuários e Convites

## Fonte original do consolidado
Modificação imediata de permissões no sistema com histórico de alterações gravado.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Editar HierarchyLevel/Department respeitando transferência organizacional.

### Requisitos reconciliados
- [ ] Alteração de nível reflete permissões atuais.
- [ ] Ao trocar Department, encerrar memberships antigas.
- [ ] Novas Areas atribuídas explicitamente.
- [ ] Registrar auditoria se prevista.

### Motivo
ADR-05 define ownership de UserAreaMembership.

## Área proprietária
`iam`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-04`
- `ADR-05`
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
