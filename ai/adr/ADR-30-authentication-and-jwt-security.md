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

## Emenda — cookie de navegador no BFF (22/10/2026, SPI-94 / FEAT-DASH-001)

O Spring mantém JWT Bearer stateless de 30 minutos e revalida usuário/tenant no
banco a cada request. Para o navegador, o BFF estabelece cookie `qsoft_access`
HttpOnly, SameSite=Strict, Path=/, Secure em produção e duração de 30 minutos.
O JWT não volta no JSON do login do BFF nem fica em local/sessionStorage.
O BFF traduz cookie para Authorization e não confia em ID de usuário enviado pelo
cliente. Cookie prevalece sobre Bearer explícito quando ambos estiverem presentes.

Rotas pessoais do dashboard usam o ID autenticado mesmo para contas administrativas.
Dados históricos empresariais não são fallback para ausência de dados pessoais.
O indicador local `qsoft.session` serve só à navegação e não concede acesso.
A abertura do dashboard valida a sessão pelo backend antes de renderizar.

CSRF da API Spring continua desabilitado para Bearer; o BFF exige header
`X-Qsoft-Request: 1` e origem válida nas operações autenticadas por cookie,
incluindo login/logout. Rejeita Sec-Fetch-Site cross-site. Atrás de TLS/proxy,
configurar BFF_PUBLIC_ORIGIN com a origem externa exata e HTTPS em produção.
Logout expira o cookie no BFF; não introduz revogação global de JWT nem refresh.
Alternativa de token opaco persistido no banco (exemplo CadastroPessoas) foi
considerada; preservou-se o emissor/validador JWT e os contratos existentes.

## Emenda — troca de contas na demonstração local (05/11/2026)

Na demo local, o BFF aceita guardar uma conta autenticada por hierarquia em
cookies HttpOnly e SameSite=Strict, restritos à rota de troca. O seletor funciona
com QSOFT_DASHBOARD_DEMO=true em loopback e fora de produção. O Spring revalida
o token e o papel antes de cada troca. A listagem retorna somente a hierarquia
e o indicador de conta ativa. Nenhuma senha ou token vai ao storage do navegador.

O ambiente local pode configurar APP_SECURITY_JWT_TTL_MINUTES=10080 com
SERVER_ADDRESS=127.0.0.1. A validade do cookie respeita a expiração assinada
e o máximo de sete dias. Sair encerra a sessão e remove todas as contas salvas.
A duração padrão continua 30 minutos fora da configuração local da demo.
