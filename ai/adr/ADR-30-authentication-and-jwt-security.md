# ADR-30 — Authentication and JWT Security

## Status

ACCEPTED

## Contexto

Autenticação multi-tenant precisa identificar a organização antes do usuário e permitir revogação efetiva.

## Decisão

Login: tenantSlug+email+password, BCrypt, JWT Bearer stateless de 30 min, sem refresh no MVP. Claims estáveis: sub/UserId, tenant_id, iat/exp. Tenant/User/permissões/escopos são revalidados no IAM. CSRF off para REST stateless, CORS allowlist, HTTPS fora do local, rate limit no login e logs sanitizados.

## Alternativas consideradas

Session server-side; email global; roles dentro do token como autoridade; JWT em URL; refresh token no MVP.

## Consequências

Reduz estado de sessão e mantém revogação rápida; cada request protegido consulta estado mutável necessário no IAM.

## Emenda (ADR-32) — 2026-10-18

O `tenantSlug` no login é **opcional**. Quando ausente, o tenant é resolvido pelo
**domínio de e-mail cadastrado** (`iam_tenants.email_domain`, único globalmente) e, para
tenants legados sem domínio, pelo fallback do primeiro rótulo do domínio (ADR-32 e sua
emenda SPI-54). O `tenant_id` das operações protegidas continua vindo exclusivamente do
JWT. O Status permanece `ACCEPTED`.
