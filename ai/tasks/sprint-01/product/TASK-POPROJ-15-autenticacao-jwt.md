# TASK-POPROJ-15 — Autenticação de usuário via e-mail e senha (JWT stateless)

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-15
- **Jira SM:** SPI-1
- **Story Points:** 6
- **Status no consolidado:** To-Do
- **Frente / Épico:** Core & Acessos (EPIC-01)
- **Tipo:** Backend / IAM + Security

## Objetivo

Implementar autenticação stateless conforme a baseline de segurança do projeto.

## Escopo

- Login usa `tenantSlug + email + password`.
- Senha é armazenada/verificada com BCrypt.
- Emitir JWT Bearer com identidade estável.
- Revalidar estado atual de Tenant e User nas operações protegidas.
- Integrar TenantContext após validação criptográfica do token.

## Fora de escopo

- Refresh token no MVP.
- Sessão HTTP stateful.
- Permissões mutáveis completas embutidas no token.

## Critérios de aceitação

- [ ] `POST /api/v1/auth/login` autentica credenciais válidas no tenant correto.
- [ ] Credenciais inválidas retornam erro genérico, sem revelar qual campo falhou.
- [ ] JWT contém somente claims estáveis previstos na arquitetura.
- [ ] User INACTIVE e Tenant INACTIVE bloqueiam acesso protegido mesmo com token ainda válido.
- [ ] TTL do access token é 30 minutos.
- [ ] Testes de autenticação e segurança passam.

## Módulo proprietário / área

`iam` + `platform` security

## Dependências

- POPROJ-13 — Tenant.
- POPROJ-14 — User.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ai/testing.md`
- `ai/adr/ADR-30-authentication-and-jwt-security.md`

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/iam/`
- `src/main/java/br/com/qsoft/governance/platform/config/`
- `src/test/java/br/com/qsoft/governance/`

## Checks

- `./scripts/check --changed`
- `./scripts/check`

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
