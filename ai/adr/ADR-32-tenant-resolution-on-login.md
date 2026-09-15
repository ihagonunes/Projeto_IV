# ADR-32 — Tenant resolution on login (email-domain fallback)

## Status

ACCEPTED

## Contexto

A autenticação precisa identificar a organização (tenant) antes de autenticar o
usuário (ADR-30: `tenantSlug + email + password`). Porém o formulário de login
entregue (frontend) possui apenas **e-mail e senha**, e a introdução de um campo
`tenantSlug` mudaria a experiência/design da tela.

O `tenantSlug` é um dado de **contexto do ambiente** (deployment), não uma
credencial que o usuário deva digitar em todo login.

## Decisão

O `tenantSlug` no login passa a ser **opcional**:

1. Se informado, é usado diretamente (cenário multi-tenant explícito).
2. Se ausente, o tenant é **derivado do domínio do e-mail** — primeiro rótulo do
   domínio (ex.: `admin@qsoft.com` → slug `qsoft`) — e resolvido por slug.
3. Se nenhum tenant corresponder, o resultado é o erro genérico de credenciais
   (`InvalidCredentialsException`), sem revelar a causa.

O `tenant_id` continua vindo **exclusivamente** do contexto autenticado (JWT) nas
operações protegidas; a derivação aplica-se apenas à resolução do tenant no login.

## Alternativas consideradas

- **Exigir `tenantSlug`** (contrato estrito do ADR-30): exigiria adicionar o campo
  ao formulário e quebraria o login atual do frontend.
- **Buscar o usuário por e-mail globalmente** (sem tenant): enfraquece o
  isolamento multi-tenant e a unicidade por tenant.
- **Resolver por subdomínio/IdP**: exige infraestrutura adicional (fora do MVP).

## Consequências

- Positivo: login funciona apenas com e-mail + senha; `tenantSlug` explícito
  permanece suportado para cenários multi-tenant.
- Negativo/risco: a heurística de domínio pode causar colisão ou direcionamento
  indevido se o slug do tenant não corresponder ao primeiro rótulo do domínio de
  e-mail. Deve ser reavaliada quando houver um mecanismo formal de resolução de
  tenant (subdomínio, IdP/SSO ou seleção explícita na tela).
- Restrição: o slug do tenant e o domínio de e-mail dos usuários devem ser
  consistentes enquanto esta decisão vigorar.
