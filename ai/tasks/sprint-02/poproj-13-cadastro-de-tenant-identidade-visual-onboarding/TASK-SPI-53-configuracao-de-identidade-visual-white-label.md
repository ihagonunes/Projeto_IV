# TASK-SPI-53 — Configuração de identidade visual (white-label)

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-53
- **História PO:** POPROJ-13
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Cadastro de Tenant, Identidade Visual & Onboarding

## Fonte original do consolidado
Upload de logotipo isolado por tenant e customização de cores primárias da UI.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Implementar white-label tenant-aware fora das regras de domínio IAM.

### Requisitos reconciliados
- [ ] Logo/cores isolados por TenantId.
- [ ] Não misturar configuração visual com autenticação/permissão.
- [ ] Ownership explícito se persistido.

### Motivo
White-label é válido, mas não deve contaminar o domínio IAM.

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
