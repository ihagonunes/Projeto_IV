# TASK-POPROJ-51 — Isolamento multi-tenant por tenant_id em todas as entidades

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-51
- **Jira SM:** SPI-1
- **Story Points:** 16
- **Status no consolidado:** To-Do
- **Frente / Épico:** Core & Acessos (EPIC-01)
- **Tipo:** Arquitetura + backend

## Objetivo

Estabelecer a fundação multi-tenant para que dados corporativos de um tenant nunca sejam lidos ou alterados por outro tenant.

## Escopo

- Criar tipos transversais de TenantId/TenantContext no Shared Kernel.
- Garantir `tenant_id` nas entidades empresariais consultadas independentemente.
- Aplicar isolamento no acesso a dados e nas operações protegidas.
- Cobrir SQL nativo tenant-aware com filtro explícito.
- Adicionar testes de isolamento entre Tenant A e Tenant B.

## Fora de escopo

- Banco físico separado por tenant.
- Schema separado por tenant.
- Confiar em `tenant_id` enviado pelo frontend como fonte de verdade.

## Critérios de aceitação

- [ ] TenantContext só é estabelecido a partir de identidade/autenticação válida.
- [ ] Entidades empresariais relevantes persistem `tenant_id` conforme a arquitetura.
- [ ] Queries nativas tenant-aware filtram explicitamente por `tenant_id`.
- [ ] Tenant A não consegue ler nem alterar recurso do Tenant B.
- [ ] ArchUnit e testes de integração continuam passando.

## Módulo proprietário / área

`sharedkernel`, `iam` e persistência tenant-aware dos módulos afetados

## Dependências

- POPROJ-57 — fundação do repositório/ambiente.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/testing.md`
- `ai/adr/ADR-03-multi-tenancy.md`

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/sharedkernel/`
- `src/main/java/br/com/qsoft/governance/iam/`
- `src/main/resources/db/migration/`
- `src/test/java/`

## Checks

- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
