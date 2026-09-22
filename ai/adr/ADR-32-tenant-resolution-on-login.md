# ADR-32 — Tenant resolution on login (email-domain fallback)

## Status

ACCEPTED (emendado pela SPI-54 — ver *Emenda* ao final)

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

## Emenda (SPI-54) — resolução pelo domínio cadastrado

O risco de colisão/ambiguidade registrado acima foi **eliminado**: cada tenant
passa a ter um **domínio de e-mail corporativo único**, persistido em
`iam_tenants.email_domain` (migration `V007`), e a sobreposição é impedida no
cadastro (`RegisterTenant` → `TenantDomainAlreadyExistsException` → HTTP 409).

A resolução do tenant no login, quando o `tenantSlug` não é informado, passa a ser:

1. slug explícito, se informado;
2. **domínio de e-mail cadastrado** no tenant
   (`TenantDomainRepository.findByEmailDomain`) — determinístico, pois o domínio
   é único globalmente;
3. fallback legado: primeiro rótulo do domínio como slug — **apenas** para
   tenants que ainda não cadastraram domínio (legado).

Consequências desta emenda:

- Positivo: a resolução deixa de depender da heurística "primeiro rótulo";
  um tenant cujo domínio não corresponda ao slug passa a ser resolvido
  corretamente (coberto por `AuthenticateUserTest.shouldResolveTenantByRegisteredEmailDomain`).
- Restrição: `emailDomain` passa a ser **obrigatório** no cadastro de tenant
  (`POST /api/v1/tenants`); o onboarding/frontend precisa enviar o campo.
- Restrição: tenants legados sem domínio continuam usando o passo 3 até que o
  domínio seja cadastrado.
