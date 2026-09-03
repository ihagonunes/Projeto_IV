# Lessons Learned & Decisions — Auditoria do uso de IA

> Documento de auditoria para os membros do projeto: registra **como a IA foi usada**, **quais decisões foram tomadas** (e com qual base) e **quais alterações foram feitas no repositório**.

- **Data:** 2026-09-02
- **Branch de trabalho:** `IhanDevelopment`
- **Agente:** IA (assistente de implementação, modelo `deepseek-v4-flash-vision-exp`)
- **Escopo:** Fundação do MVP — frontend + módulo IAM (EPIC-01) + correções de baseline
- **Precedência seguida:** `AGENTS.md` → `ai/context-map.md` → task/épico → `ai/architecture.md`/`ai/contracts.md` → ADR relacionado → código do módulo proprietário.

---

## 1. Resumo executivo

O repositório partiu de um **monólito modular documentado de forma rica** (`ai/*`, 31 ADRs, 5 épicos) mas com **backend vazio** (módulos eram apenas `.gitkeep`) e um **frontend Node/Express/EJS legado** quebrado e acoplado a MySQL.

A IA, de forma incremental e sempre guiada pelos ADRs/documentação,:

1. Conectou o frontend à arquitetura (removeu MySQL; proxy BFF; cliente de API + auth; `tenantSlug` via BFF).
2. Implementou o módulo **IAM** do zero: Tenant, User, Hierarquia, Autenticação JWT, Isolamento multi-tenant e RBAC (POPROJ-13/14/16/15/51/52), fechando a **EPIC-01 (Core & Acessos)**.
3. Corrigiu dois bugs de baseline (Flyway não rodava no Spring Boot 4; isolamento por `find()`).
4. Extraiu os DOCX de arquitetura para Markdown (autoridade legível por IA).

**Resultado:** `mvn test` (34 testes unit/ArchUnit) e `mvn verify -Pintegration` (14 ITs com PostgreSQL real) passam.

---

## 2. Decisões tomadas pela IA (com racional e base)

| # | Decisão | Racional / Base | Onde refletida |
|---|---|---|---|
| D1 | **Não expor endpoint público de criação de usuário** (só caso de uso + persistência). Criar usuário é mutação autorizada; exigiria JWT/RBAC. | ADR-04, ADR-28, AGENTS §16 (segurança) | `CreateUser` (application), sem `UserController` |
| D2 | **Autorização escopada por `sameTenant`** (e depois + permissão ADMIN) para alterar nível. | ADR-03, ADR-04, §22 | `ChangeUserHierarchyLevel` |
| D3 | **`tenant_id` e `tenantSlug` nunca vêm do cliente**; `TenantContext` só do JWT. | ADR-03, ADR-30 | `TenantContextFilter`, `TenantContextHolder` |
| D4 | **`department_id` persistido como ID cross-module sem FK SQL**. | ADR-05, ADR-29 | migrations V002; `app_user.department_id UUID` |
| D5 | **ADMIN = conjunto completo de permissões**, não nível hierárquico. | ADR-04 | `Permission` enum; `User.isAdmin()` |
| D6 | **JWT só com claims estáveis** (`sub`, `tenant_id`, `iat`, `exp`), sem permissões embutidas. | ADR-30 | `JwtTokenIssuer` |
| D7 | **Segredo JWT apenas dev por default**, sobrescrito via `JWT_SECRET` em produção. | AGENTS §16 (nunca commitar segredo real) | `application.yml` |
| D8 | **`UserContext`/`IdentityQuery` expõem nível/status como `String`** para não vazar tipos de `iam.domain` além de `module.api.*`. | ADR-31 (cross-module só via `api.*`) | `iam.api.model.UserContext` |
| D9 | **Hibernate `@Filter` é defesa SECUNDÁRIA**; a primária é o filtro explícito por `tenant_id` nas queries. | ADR-03 | `UserJpaEntity`, `UserRepositoryAdapter` |
| D10 | **`findById` restringido por tenant via `findByIdAndTenantId`** (o `@Filter` do Hibernate não se aplica ao `find()` por PK). | Hibernate 7 + Spring Data behavior | `UserJpaRepository`, `UserRepositoryAdapter` |
| D11 | **Módulo `spring-boot-flyway` adicionado** — no Spring Boot 4 a auto-config do Flyway foi movida para módulo dedicado. | Boot 4 / Flyway 12 | `pom.xml` |
| D12 | **Escopo do POPROJ-52 (RBAC) derivado de ADR-04 + contracts.md + §22**, pois não havia task packet no repositório. | AGENTS §2 (não inventar requisitos) | commit `1eea901` |
| D13 | **DOCX (`docs/reference/*.docx`) extraídos para Markdown** e mantidos como autoridade; `ai/` e ADRs continuam canônicos. | `docs/reference/README.md` (precedência) | `docs/reference/*.md` |
| D14 | **Frontend consome o backend via BFF proxy `/api/*`** (same-origin), sem CORS/host fixo no browser. | ADR-28, hints "BFF" no código | `static/app.js` |
| D15 | **`tenantSlug` injetado via BFF** (`window.QSOFT_TENANT_SLUG`) evitando mudar o design do formulário. | ADR-30 (login exige slug); preservar design | `app.js`, `cabecalho.ejs`, `auth.js` |

---

## 3. Alterações por área

### 3.1 Frontend (Express/EJS → conectado à arquitetura)
- **Removido** o acoplamento a MySQL: `require("./data/sql")` (módulo inexistente) e a dependência `mysql2`.
- **Adicionado** proxy BFF `/api/* → Spring Boot` (target `API_PROXY_TARGET`, padrão `http://localhost:8080`), registrado antes do `express.json`.
- `public/js/core/api-client.js` — wrapper de `fetch` para `/api/v1`, JSON, `Authorization: Bearer`, tratamento de erro.
- `public/js/core/auth.js` — `login()` (`POST /api/v1/auth/login`), persistência de token (`qsoft.access_token`), `logout`, `requireAuth`; usa `tenantSlug` do chamador ou `window.QSOFT_TENANT_SLUG`.
- `public/js/pages/login.js` — submit autentica no backend (sucesso → `/onboarding`; falha → banner de erro), mantendo a validação existente.
- `app.locals.qsoftTenantSlug` + `views/comum/cabecalho.ejs` injeta `window.QSOFT_TENANT_SLUG` (opção 1, sem alterar design do formulário).
- `.env.example` — `API_PROXY_TARGET`, `QSOFT_TENANT_SLUG`, `JWT_SECRET` (placeholder).

### 3.2 Módulo IAM — backlog EPIC-01
**sharedkernel:** `id/TenantId`, `id/UserId`, `tenant/TenantContext`, `tenant/TenantContextHolder`.

**POPROJ-13 — Cadastro de Tenant:**
- domínio `Tenant`, `TenantStatus`; exceção `TenantSlugAlreadyExistsException`.
- aplicação `RegisterTenant` + `RegisterTenantCommand` + porta `TenantRepository`.
- infra: `TenantJpaEntity`, `TenantJpaRepository`, `TenantJpaMapper`, `TenantRepositoryAdapter`.
- web: `TenantController` (`POST /api/v1/tenants`), `TenantRequest`/`TenantResponse`, `TenantApiExceptionHandler` (409 duplicado / 400 validação).
- migration `V001__create_iam_tables.sql`; endpoint liberado no Security (onboarding).

**POPROJ-14 — Cadastro de Usuários:**
- domínio `User`, `UserStatus`, `HierarchyLevel` (COLLABORATOR/LEADER/MANAGER/EXECUTIVE; ADMIN fora do enum).
- exceções `UserEmailAlreadyExistsException`, `TenantNotFoundException`.
- aplicação `CreateUser` + `CreateUserCommand` + porta `UserRepository`; `TenantRepository.findById`.
- infra JPA de User; migration `V002__create_iam_user.sql` (FK intramodule p/ tenant, `unique(tenant_id,email)`); `department_id` como ID sem FK.
- **Sem endpoint HTTP** (decisão D1).

**POPROJ-16 — Hierarquia:**
- `User.changeHierarchyLevel(HierarchyLevel)`.
- `iam.api`: `IdentityQuery`, `model/UserContext`.
- `ChangeUserHierarchyLevel` (+ `ChangeUserHierarchyLevelCommand`) — exige `sameTenant`; `UserRepository.findById`.
- `IdentityQueryAdapter`; exceções `UserNotFoundException`, `AuthorizationDeniedException`.

**POPROJ-15 — Autenticação JWT:**
- `User.passwordHash` (hash BCrypt) + migration `V003__add_password_hash.sql`.
- `AuthenticateUser` (login: tenantSlug+email+password; Tenant/User ACTIVE; erro genérico `InvalidCredentialsException`) + `AuthenticatedUser` + porta `TokenIssuer`.
- `JwtSecurityProperties` + `JwtConfig` (beans `JwtEncoder`/`JwtDecoder` HS256); `JwtTokenIssuer` (claims estáveis, TTL 30 min).
- `AuthController` (`POST /api/v1/auth/login`) + `AuthRequest`/`AuthResponse` + `AuthExceptionHandler`.
- `TenantContextFilter` (revalida Tenant/User ACTIVE + mesmo tenant; propaga `TenantContext`).
- `SecurityBaselineConfig`: `oauth2ResourceServer(jwt)`, `anyRequest().authenticated()`, login/health/tenant `permitAll`, 401/403 JSON.

**POPROJ-51 — Isolamento multi-tenant:**
- `@FilterDef`/`@Filter` (tenantFilter) em `UserJpaEntity` (defesa secundária) + ativação via `TenantContextHolder` no `UserRepositoryAdapter`.
- Teste de isolamento `shouldNotReadOtherTenantUserWhenContextIsSet`.
- **Ajuste (committed como fix):** `findByIdAndTenantId` + `findById` tenant-aware (decisão D10).

**POPROJ-52 — RBAC:**
- `iam.api.model`: `Permission`, `AuthorizationScope`, `AuthorizationResult`; `iam.api.query.AuthorizationQuery`.
- `User.permissions` (`@ElementCollection`) + migration `V004__create_user_permission.sql`.
- `AuthorizationQueryAdapter` (User/Tenant ACTIVE + permissão + escopo por hierarquia).
- `ChangeUserHierarchyLevel` agora exige `MANAGE_HIERARCHY` (além de mesmo-tenant).

### 3.3 Correções de baseline
- `pom.xml`: adicionado `spring-boot-flyway` (decisão D11).
- `UserJpaRepository.findByIdAndTenantId` + `UserRepositoryAdapter.findById` tenant-aware (decisão D10).

### 3.4 Documentação / referência
- `docs/reference/Arquitetura_Funcional_Plataforma_Governanca_IA_QSOFT_Final.md` (extraído do DOCX).
- `docs/reference/QSOFT_Demonstracao_Arquitetura_C4_Final.md` (extraído do DOCX).
- `docs/reference/README.md`: listadas as versões em Markdown.

---

## 4. Testes adicionados / validados

- **Unitários (mvn test, 34):** ArchUnit (fronteiras modulares) + `RegisterTenantTest`, `CreateUserTest`, `ChangeUserHierarchyLevelTest`, `AuthenticateUserTest`, `IdentityQueryTest`, `JwtTokenIssuerTest`, `TenantContextFilterTest`.
- **Integração (mvn verify -Pintegration, 14, Testcontainers/PostgreSQL real):** `TenantPersistenceIT`, `UserPersistenceIT`, `AuthenticationIT`, `AuthorizationIT`, `PostgreSqlSmokeIT`. Flyway aplica `V001`–`V004`; `ddl-auto=validate` passa; isolamento A ≠ B validado.

> `scripts/check-integration`/`mvn verify -Pintegration` exige Docker (Testcontainers).

---

## 5. Riscos / observações / pendências

- **Segredo JWT:** o default em `application.yml` é **apenas dev**; em produção definir `JWT_SECRET` forte.
- **Login ponta-a-ponta:** requer `QSOFT_TENANT_SLUG` configurado (ou slug fornecido) no BFF/front; o formulário mantém só e-mail/senha.
- **Usuários criados via `CreateUser` sem senha inicial** — o fluxo de senha/convite é tarefa à parte; login rejeita usuário sem hash.
- **`resolveScope`/`Permission`** derivados da baseline (§22/§14.6) — revisar com o time se o catálogo de permissões deve ser ampliado.
- **Escopo POPROJ-52** não tinha task packet no repo; foi reconstruído a partir de ADR-04/§22.
- **Não implementado / adiado por segurança:** endpoint de criação de usuário (aguardar utilização do JWT nos controllers), RBAC aplicado a outros módulos (ainda inexistentes), ajuste fino do front para o erro 401/403, validação de `department_id` (módulo `department` ainda não existe).
- **Fora de escopo (não alterado):** arquivos pré-existentes de terceiros `.idea/misc.xml`, `ai/tasks/README.md`, `ai/tasks/sprint-01/`.
- **EPIC-002 (Gateway/Chat/Agent Policies) NÃO faz parte da Sprint 1** — é de sprint futura.

---

## 6. Registro de commits (o que foi alterado)

| Commit | Mensagem | Conteúdo |
|---|---|---|
| `09c4db2` | `feat(SPI-1): implementar cadastro de tenant e usuário e integrar frontend` | Frontend conectado (MySQL removido, proxy BFF, api-client/auth/login) + POPROJ-13 (Tenant) + POPROJ-14 (User) + DOCX extraídos + Security (POST /api/v1/tenants) |
| `f8bb2bf` | `feat(POPROJ-16): definir e alterar níveis hierárquicos no IAM` | `User.changeHierarchyLevel`, `iam.api` (`IdentityQuery`/`UserContext`), `ChangeUserHierarchyLevel`, `UserRepository.findById` |
| `3f59de8` | `feat(POPROJ-15): autenticação JWT stateless (login + TenantContext)` | `TenantContext`, `User.passwordHash` (V003), `AuthenticateUser`, `JwtTokenIssuer`, `AuthController`, `TenantContextFilter`, resource server JWT |
| `d28e93a` | `feat(POPROJ-51): isolamento multi-tenant por tenant_id (defesa transversal)` | `@Filter` Hibernate + ativação; teste de isolamento A ≠ B |
| `1eea901` | `feat(POPROJ-52): controle de acesso RBAC (AuthorizationQuery)` | `Permission`/`AuthorizationScope`/`AuthorizationResult`, `AuthorizationQuery`+adapter, `User.permissions` (V004), `MANAGE_HIERARCHY` no `ChangeUserHierarchyLevel` |
| `38f5e87` | `feat(SPI-1): injetar tenantSlug no login via BFF (Express)` | `app.locals.qsoftTenantSlug`, `cabecalho.ejs` → `window.QSOFT_TENANT_SLUG`, `auth.js` |
| `a838e94` | `fix(SPI-1): aplicar migrations Flyway e isolar findById por tenant` | `spring-boot-flyway` no pom; `findByIdAndTenantId`/`findById` tenant-aware |

---

## 7. Como a IA foi usada (fluxo)

1. **Contexto progressivo** — `AGENTS.md` → `ai/context-map.md` → task atual → `ai/architecture.md`/`ai/contracts.md` → ADR relacionado → código do módulo proprietário.
2. **Fronteira da mudança** — identificação do módulo proprietário (`iam`, `sharedkernel`, `platform`) antes de codificar; mudança mínima e reversível.
3. **Implementação em camadas** — `domain` (puro, sem Spring/JPA) → `application` (ports + casos de uso) → `infrastructure` (JPA/adapters/web) → `api` (contratos públicos `module.api.*`).
4. **Validação** — `mvn test` (unit + ArchUnit) e `mvn verify -Pintegration` (ITs com PostgreSQL real/Testcontainers).
5. **Segurança como invariante** — `tenant_id`/`TenantContext` do JWT (nunca do cliente); mutações autorizadas; RBAC centralizado no IAM; erros 401/403 sem vazar stack trace.
6. **Revisão** — `git diff` revisado antes do commit; commits separados e convencionais (`feat`/`fix` com chave Jira), sem misturar mudanças não relacionadas.

---

*Documento gerado pela IA para auditoria. Revisar com a equipe: catálogo de permissões, escopo `resolveScope`, decisões de segurança e pendências da seção 5.*
