# TASK-SPI-56 — Auditoria de criação/alteração do tenant

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-56
- **História PO:** POPROJ-13
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Cadastro de Tenant, Identidade Visual & Onboarding

## Fonte original do consolidado
Registro de trilha de auditoria (quem alterou, timestamp e campos).

## Versão reconciliada com a arquitetura

### Objetivo técnico
Registrar auditoria de Tenant sem segredos.

### Requisitos reconciliados
- [ ] Registrar ator, timestamp e campos alterados.
- [ ] Não registrar senha/JWT/segredos.
- [ ] Manter tenant-aware.

### Motivo
Compatível com segurança/log sanitization.

## Área proprietária
`iam + presentation`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ADR-03`
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
