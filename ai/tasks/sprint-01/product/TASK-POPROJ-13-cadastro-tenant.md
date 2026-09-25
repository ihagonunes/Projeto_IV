# TASK-POPROJ-13 — Cadastro de organização (tenant) na plataforma

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-13
- **Jira SM:** SPI-1
- **Story Points:** 11
- **Status no consolidado:** To-Do
- **Frente / Épico:** Core & Acessos (EPIC-01)
- **Tipo:** Backend / IAM

## Objetivo

Permitir o cadastro da organização que representa o tenant raiz da plataforma.

## Escopo

- Modelar Tenant no módulo IAM.
- Persistir identificação necessária da organização.
- Garantir unicidade do identificador usado para localizar o tenant no login.
- Expor caso de uso/API de cadastro conforme a API `/api/v1`.

## Fora de escopo

- Provisionamento de infraestrutura física por cliente.
- Banco separado por tenant.
- SSO no MVP.

## Critérios de aceitação

- [ ] Tenant pode ser criado por caso de uso do módulo IAM.
- [ ] O identificador/slug de tenant não é duplicado.
- [ ] Tenant possui estado compatível com as regras de ativação/desativação.
- [ ] Persistência é criada por migration Flyway.
- [ ] Testes do caso de uso e persistência passam.

## Módulo proprietário / área

`iam`

## Dependências

- POPROJ-51 — fundação multi-tenant.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ai/adr/ADR-04-iam.md`
- `ai/adr/ADR-30-authentication-and-jwt-security.md`

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/iam/`
- `src/main/resources/db/migration/`
- `src/test/java/br/com/qsoft/governance/`

## Checks

- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
