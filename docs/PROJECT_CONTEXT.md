# PROJECT_CONTEXT — Estado Atual do Repositório

> ⚠️ **SUPERADO (2026-10-18). Não use este arquivo como estado atual.**
> Ele descreve o repositório em 14/09/2026. Desde então, entre outras mudanças:
> - os módulos `department`, `aigateway` e `observability` foram implementados;
> - as migrations vão até a V014;
> - a suíte tem ~800 testes;
> - os endpoints de negócio são protegidos por RBAC.
>
> Estado atual e verificado: `docs/review/VERIFICACAO_FASE0.md`.
> Plano em execução: `ai/tasks/sprint-03/INDEX.md`.
> Divergências da documentação: `docs/review/AUDITORIA_DOCUMENTACAO.md`.
> O conteúdo abaixo fica só como registro histórico.

> Contexto compacto e factual do estado atual do repositório, destinado a
> planejamento técnico (ex.: uso como contexto para agentes com integração
> Jira na descrição de tasks). Data de referência: 2026-09-14.
> Este documento descreve **o que existe** no código. Nada aqui é especulado.

---

## 1. Identificação do projeto

- **Produto:** Plataforma QSOFT de Governança Corporativa de IA (white-label, multi-tenant).
- **Projeto acadêmico:** Projeto Integrador IV (SM_Projeto_IV), parceria ESPM + QSOFT.
- **Repositório:** `qsoft-ai-governance` (`br.com.qsoft:qsoft-ai-governance`, versão `0.0.1-SNAPSHOT`).
- **Problema que resolve:** reduzir Shadow AI e governar uso corporativo de IA
  (centralizar acesso governado, políticas internas, observabilidade de custo,
  engajamento e indicadores executivos).
- **Chaves Jira em uso no repositório:** `SPI-*` (tasks) e `POPROJ-*` (stories).
  Commits recentes usam `SPI-1`, `feat(iam)`, etc. (ver seção 12).

---

## 2. Baseline técnica (implementada de fato)

| Item | Valor | Onde |
|---|---|---|
| Java | 21 | `pom.xml` |
| Spring Boot | 4.1.1 | `pom.xml` (parent) |
| Build | Maven único (monólito) | `pom.xml` |
| Persistência | Spring Data JPA + Hibernate (`ddl-auto=validate`) | `application.yml` |
| SGBD | PostgreSQL (único suportado; **sem H2**) | `pom.xml`, `application.yml` |
| Migrations | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`), `classpath:db/migration` | `pom.xml`, `application.yml` |
| Segurança | Spring Security + OAuth2 Resource Server (JWT Bearer) | `SecurityBaselineConfig` |
| Hash de senha | BCrypt (`BCryptPasswordEncoder`) | `SecurityBaselineConfig` |
| Validação | Bean Validation (`spring-boot-starter-validation`) | controllers/DTOs |
| OpenAPI/Swagger | springdoc-openapi 3.1.0 (UI em `/swagger-ui.html`) | `OpenApiConfig` |
| Health | Actuator (`/actuator/health`, probes habilitadas) | `application.yml` |
| Testes | JUnit 5, Spring Boot Test, ArchUnit 1.4.2, Testcontainers 2.0.5, JaCoCo 0.8.15 | `pom.xml` |
| Frontend | Node/Express/EJS (BFF) dentro de `src/main/resources/static` | `package.json`, `app.js` |

Dependências **proibidas/pendentes de decisão** (documentadas em `ai/dependencies.md`):
H2, Lombok, MapStruct, Spring Modulith, Spring AI, Redis, Kafka/RabbitMQ,
pgvector, OpenTelemetry vendor, SDK concreto de provider de IA.

---

## 3. Arquitetura

Estilo definido em `ai/architecture.md` e ADR-01/ADR-31: **Monólito Modular**,
um único Maven, um único PostgreSQL. Pacote raiz: `br.com.qsoft.governance`.

Estrutura de módulos Java (cada um com `api/`, `domain/`, `application/`,
`infrastructure/`; exceção `sharedkernel` e `platform`):

```
sharedkernel, iam, department, project, aigateway, chat,
agentpolicies, observability, engagement, governancescore, platform
```

### 3.1 Estado real de implementação por módulo

| Módulo | Estado atual | Conteúdo real |
|---|---|---|
| `sharedkernel` | Implementado (parcial) | `id/TenantId`, `id/UserId`, `tenant/TenantContext`, `tenant/TenantContextHolder`; demais subpacotes vazios (`event/`, `exception/`, `id/.gitkeep`, `tenant/.gitkeep`) |
| `iam` | Implementado (core do MVP) | Ver seções 4–7 |
| `platform` | Implementado (config) | `config/JwtConfig`, `JwtSecurityProperties`, `OpenApiConfig`, `SecurityBaselineConfig` |
| `department` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `project` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `aigateway` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `chat` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `agentpolicies` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `observability` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `engagement` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |
| `governancescore` | **Placeholder** (apenas `.gitkeep`) | Nenhum código |

Classe principal: `br.com.qsoft.governance.GovernanceApplication`.

### 3.2 Fronteiras impostas por ArchUnit (`ArchitectureRulesTest`)

- `domain` não depende de Spring/JPA/`application`/`infrastructure`.
- `sharedkernel` não depende de módulos de negócio.
- Dependência cross-module somente via `<module>.api.*` e pela allowlist:
  `department→iam`, `project→iam,department`, `aigateway→department`,
  `chat→iam,aigateway`, `agentpolicies→iam,department,aigateway`,
  `observability→iam,department`, `engagement→iam,observability,aigateway`,
  `governancescore→iam,engagement,observability`.
- Pacotes de primeiro nível livres de ciclos.

### 3.3 Princípios arquiteturais vigentes (dos ADRs/AGENTS.md)

- FK e associação JPA **cross-module proibidas**; relações cross-module são
  IDs + Command/Query Port ou Domain Event (ADR-02/ADR-29).
- `TenantId` vem do contexto autenticado; nunca de valor enviado pelo cliente (ADR-03).
- Query/SQL nativo tenant-aware exige filtro explícito de `tenant_id`.
- LLM nunca decide autorização; chat é privado inclusive de MANAGER/ADMIN.
- Sem endpoint público `/ai/complete`.

---

## 4. Módulo IAM — o que está implementado

### 4.1 `domain`

- `tenant/Tenant` (id, slug, name, status; `create`/`restore`/`activate`/`deactivate`) e `tenant/TenantStatus` (ACTIVE/INACTIVE).
- `user/User` (id, tenantId, email, name, hierarchyLevel, departmentId [UUID, sem FK], passwordHash, permissions, status) com `create`, `restore`, `setPasswordHash`, `grantPermission`/`revokePermission`, `isAdmin` (conjunto completo de permissões), `activate`/`deactivate`, `changeHierarchyLevel`.
- `user/HierarchyLevel`: `COLLABORATOR, LEADER, MANAGER, EXECUTIVE` (ADMIN **não** é nível hierárquico).
- `user/UserStatus`: ACTIVE/INACTIVE.
- Exceções: `AuthorizationDeniedException`, `InvalidCredentialsException`, `TenantNotFoundException`, `TenantSlugAlreadyExistsException`, `UserEmailAlreadyExistsException`, `UserNotFoundException`.

### 4.2 `api` (superfície pública do módulo)

- `api/model/Permission` (enum): `MANAGE_TENANT, MANAGE_USERS, MANAGE_HIERARCHY, MANAGE_DEPARTMENTS, MANAGE_MODEL_POLICIES, MANAGE_AI_CATALOG, MANAGE_KNOWLEDGE, MANAGE_LICENSES, MANAGE_TRAINING`.
- `api/model/AuthorizationScope`: `SELF, DEPARTMENT, AREA, TENANT`.
- `api/model/AuthorizationResult` e `api/model/UserContext`.
- `api/query/IdentityQuery` (`getUserContext(UserId)`) e `api/query/AuthorizationQuery` (`checkPermission`, `resolveScope`).
  Implementados por `AuthorizationQueryAdapter` e `IdentityQueryAdapter` (infrastructure/query).

### 4.3 `application` (casos de uso)

| Caso de uso | Arquivo | Comportamento |
|---|---|---|
| Autenticação | `AuthenticateUser` | `tenantSlug + email + password`; exige Tenant/User ACTIVE; erro genérico `InvalidCredentialsException`; **fallback**: se `tenantSlug` ausente, deriva do domínio do e-mail (primeiro rótulo) |
| Registro de tenant | `RegisterTenant` | slug único global; tenant nasce ACTIVE |
| Criar usuário (sem senha) | `CreateUser` | valida tenant ACTIVE, e-mail único por tenant, papel obrigatório; usuário nasce ACTIVE sem senha |
| Provisionar usuário (com senha) | `ProvisionUser` | cria com `passwordHash` BCrypt e permissões (usado pelo onboarding) |
| Registrar usuário autorizado | `RegisterUser` | exige ator com permissão `MANAGE_USERS` + mesmo tenant (delega a `CreateUser`) |
| Alterar nível hierárquico | `ChangeUserHierarchyLevel` | exige `MANAGE_HIERARCHY` + mesmo tenant |

Ports: `TenantRepository` (save, findBySlug, findById, existsBySlug), `UserRepository` (save, findById, findByTenantIdAndEmail, existsByTenantIdAndEmail), `TokenIssuer`.

### 4.4 `infrastructure`

- **Persistence:** `TenantJpaEntity`/`TenantJpaRepository`/`TenantJpaMapper`/`TenantRepositoryAdapter`;
  `UserJpaEntity`/`UserJpaRepository`/`UserJpaMapper`/`UserRepositoryAdapter`.
  - `@FilterDef`/`@Filter` (tenantFilter `tenant_id = :tenantId`) é **defesa secundária**;
    `findById`/`findByIdAndTenantId` implementam isolamento na query (defesa primária).
- **Security:**
  - `JwtTokenIssuer`: JWT HS256, claims estáveis `sub` (UserId), `tenant_id`, `iat`, `exp`; TTL `app.security.jwt.ttl-minutes` (padrão 30 min); **sem permissões no token**, sem refresh.
  - `TenantContextFilter` (`OncePerRequestFilter`): revalida Tenant/User ACTIVE + mesmo tenant do token, popula `TenantContextHolder.set(new TenantContext(tenantId, userId))`, limpa no `finally`.
- **Web:** `AuthController`, `TenantController`, `UserController` + records de request/response + handlers de erro (ver seção 5).
- **Bootstrap:** `DemoDataSeeder` (`app.bootstrap.demo-data=true` por padrão) cria, se ausente:
  tenant `qsoft` (name "QSOFT Demo") e usuário `admin@qsoft.com` — senha `Qsoft@2026`, `HierarchyLevel.EXECUTIVE`, **todas** as `Permission`.

### 4.5 `platform/config`

- `JwtConfig`: beans `SecretKey`, `JwtEncoder`, `JwtDecoder` (HMAC/HS256) a partir de `app.security.jwt.secret` (`${JWT_SECRET}`, com default **apenas dev**).
- `SecurityBaselineConfig`: stateless (`STATELESS`), CSRF off, `oauth2ResourceServer(jwt)`, CORS default, 401/403 em JSON (`Não autenticado.` / `Acesso negado.`), httpBasic/formLogin/logout desabilitados, `BCryptPasswordEncoder` bean.

---

## 5. Endpoints HTTP (todos os existentes)

Base: `/api/v1`. **Nenhum endpoint de negócio protegido ainda existe** — os 3
métodos abaixo são públicos e os demais caminhos exigem JWT válido (mas ainda
não há recurso protegido registrado).

| Método | Caminho | Controller | Payload de entrada | Resposta |
|---|---|---|---|---|
| POST | `/api/v1/tenants` | `TenantController` | `{slug, name}` (slug/name `@NotBlank`, slug ≤100, name ≤200) | 201 `TenantResponse{id, slug, name, status, createdAt}`; 409 slug duplicado; 400 validação |
| POST | `/api/v1/auth/login` | `AuthController` | `{tenantSlug?, email, password}` (email/password `@NotBlank`) | 200 `AuthResponse{token, tokenType:"Bearer"}`; 401 `InvalidCredentialsException`; 400 validação |
| POST | `/api/v1/usuarios` | `UserController` | `{tenantId, email, name, hierarchyLevel, password, departmentId?, permissions?}` | 201 `UserResponse{id, tenantId, email, name, hierarchyLevel, status}`; 409 e-mail duplicado; 404 tenant inexistente; 400 validação |

> **Atenção (estado atual):** `POST /api/v1/usuarios` é **provisão de usuário
> com senha, exposta publicamente de forma temporária** (comentado no código
> como "onboarding ainda não tem autenticação"). Não é o mesmo fluxo do
> `RegisterUser` (que exige `MANAGE_USERS`), que hoje **não tem controller HTTP**.

Públicos (permitidos na segurança): `/actuator/health/**`, `/api/v1/auth/login`,
`/api/v1/tenants`, `/api/v1/usuarios`, `/v3/api-docs/**`, `/swagger-ui/**`,
`/swagger-ui.html`. Qualquer outra rota: `authenticated()`.

---

## 6. Migrations Flyway (schema canônico da aplicação)

Local: `src/main/resources/db/migration`. Aplicadas na inicialização; Hibernate valida com `ddl-auto=validate`.

| Migration | Conteúdo |
|---|---|
| `V001__create_iam_tables.sql` | `tenant(id uuid, slug, name, status, created_at, updated_at)`; PK `id`; UNIQUE `slug`; índices por slug/created_at |
| `V002__create_iam_user.sql` | `app_user(id, tenant_id, email, name, hierarchy_level, department_id uuid [sem FK], status, created_at, updated_at)`; FK intramodule `tenant_id → tenant(id)`; UNIQUE `(tenant_id, email)`; índice por tenant_id |
| `V003__add_password_hash.sql` | `ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(255)` (nulo para usuários sem senha) |
| `V004__create_user_permission.sql` | `user_permission(user_id, permission)`; PK composta; FK `user_id → app_user(id)` |

> Schema real da aplicação: tabelas `tenant`, `app_user`, `user_permission`.
> **Divergência conhecida:** `scripts/db/create-database.sql` é um `pg_dump` de
> um banco QSOFT de referência com outro naming (`iam_tenants`, `iam_users`,
> tabelas de todos os módulos, schemas `staging`/`historical_*`). Esse script
> **não** reflete o schema canônico do Flyway e não é usado pela aplicação.
> `scripts/db/fix-crossmodule.sql` corrige FKs cross-module **nesse dump**.

---

## 7. Testes existentes (16 arquivos)

Sem Docker (ciclo rápido `mvn test` / `./scripts/check`):

| Classe | Cobre |
|---|---|
| `architecture/ArchitectureRulesTest` | ArchUnit: domain livre de framework, sharedkernel isolado, allowlist + `module.api.*`, sem ciclos |
| `iam/application/RegisterTenantTest` | registro, slug duplicado, slug em branco |
| `iam/application/CreateUserTest` | criação em tenant ativo, e-mail duplicado por tenant, mesmo e-mail em tenants diferentes, tenant inexistente/inativo, campos em branco, papel nulo, ADMIN fora do enum |
| `iam/application/ProvisionUserTest` | provisão com senha+permissões, duplicado, tenant inexistente, senha em branco |
| `iam/application/RegisterUserTest` | registro com papel, negação sem permissão, manager por departamento, sem papel |
| `iam/application/AuthenticateUserTest` | credenciais válidas, derivação de slug por e-mail, senha errada, tenant desconhecido/inativo, usuário inativo, usuário sem hash |
| `iam/application/ChangeUserHierarchyLevelTest` | mudança no mesmo tenant, falta de permissão, cross-tenant, alvo/ator inexistentes, nível nulo |
| `iam/infrastructure/query/AuthorizationQueryAdapterTest` | escopo por hierarquia (MANAGER→DEPARTMENT, COLLABORATOR→SELF, EXECUTIVE→TENANT), negação sem permissão |
| `iam/infrastructure/query/IdentityQueryTest` | contexto com hierarquia como String, usuário desconhecido |
| `iam/infrastructure/security/JwtTokenIssuerTest` | claims estáveis apenas, expiração ~TTL |
| `iam/infrastructure/security/TenantContextFilterTest` | contexto com token válido, usuário/tenant inativos, sem autenticação |

Integração (exigem Docker/Testcontainers — `mvn verify -Pintegration` / `./scripts/check-integration`; **excluídos** do `mvn test` via surefire `**/*IT.java`):

| Classe | Cobre |
|---|---|
| `iam/infrastructure/TenantPersistenceIT` | persistir tenant, slug duplicado, unknown slug |
| `iam/infrastructure/UserPersistenceIT` | persistir usuário no tenant, e-mail duplicado no mesmo tenant, mesmo e-mail em tenants diferentes, tenant inexistente, troca de nível, **isolamento A ≠ B** |
| `iam/infrastructure/AuthenticationIT` | login válido, senha errada |
| `iam/infrastructure/AuthorizationIT` | admin muda hierarquia, colaborador negado |
| `integration/PostgreSqlSmokeIT` | conexão com PostgreSQL real |

Config de teste: `src/test/resources/application.yml` (Flyway roda antes da
validação do Hibernate no contexto de teste).

---

## 8. Frontend (BFF Node + Express + EJS) — estado real

Localizado em `src/main/resources/static` (servido pelo app Express, não pelo Spring).

- **BFF** `app.js`: Express 4, EJS + `express-ejs-layouts`, proxy de `/api/*` para o backend Spring (`API_PROXY_TARGET`, padrão `http://localhost:8080`), `app.locals.qsoftTenantSlug` (injetado como `window.QSOFT_TENANT_SLUG`), cache LRU de EJS, sem acesso a banco.
- **Rotas Express** (`routes/*.routes.js`): `auth` (/login, /onboarding, /redefinir-senha), `tenant` (/tenant), `admin` (convites, step-admin), `departamentos`, `usuarios` (admin/usuarios, hub, importacao, manual), `ia`, `revisao`.
- **Controllers dos módulos** (`modules/*/*.controller.js`): proxy via axios (`config/api.js`, `baseURL = SPRING_API_URL + /api/v1`). Chamam: `/auth/login`, `/tenants`, `/usuarios`, `/admin/convites`, `/departamentos`.
- **Client-side JS** (`public/js/core`): `api-client.js` (expõe `window.ApiClient` com base `/api` e `window.API` com base `/api/v1`, token em `authToken`), `auth.js` (login via `POST /api/v1/auth/login`, persistência de token, `requireAuth`), `notifications.js`. Páginas: `login.js`, `redefinir-senha.js`, `admin-usuarios.js`, `admin-convites.js`, `neural-network.js`, onboarding steps (`step-tenant`, `step-departamentos`, `step-ia`, `step-usuarios`, `step-revisao`, `step-admin`).
- **Views/skins:** EJS de login, onboarding, redefinir-senha, tenant, hub de usuários, admin-usuários, convites, steps; layout + partials; CSS (base/components/pages); imagens de marca (atlas-logo, atlas-name); componentes JS (wizard, stepper-hub, data-grid, form-validation).
- **`package.json`:** `npm start` → `node app.js` (porta 3000). Dependências: express, ejs, axios, dotenv, cookie-parser, compression, multer, lru-cache, express-ejs-layouts, express-async-error-wrapper.

> **Divergência a considerar no planejamento:** o frontend possui telas/rotas
> para convites, departamentos, IA e revisão e controllers que chamam endpoints
> (`/admin/convites`, `/departamentos`, `/usuarios` GET/PUT, `/usuarios/importar`)
> que **não existem no backend** — apenas `/api/v1/tenants`, `/api/v1/auth/login`
> e `/api/v1/usuarios` (POST) existem de fato.

---

## 9. Scripts, CI e execução

### Scripts (`scripts/`)

| Script | Função |
|---|---|
| `scripts/check [--changed]` | Verifica arquivos obrigatórios, lista arquivos alterados (`--changed`) e roda `mvn test` |
| `scripts/check-integration` | Roda `mvn verify -Pintegration` (Testcontainers/PostgreSQL real; exige Docker) |
| `scripts/run-local.ps1` / `run-local.sh` | Sobe o backend local com PostgreSQL |
| `scripts/db/create-database.sql` | `pg_dump` de banco QSOFT de referência (naming divergente do Flyway; ver seção 6) |
| `scripts/db/fix-crossmodule.sql` | Correção de FKs cross-module e `tenant_id` ausente aplicada a esse dump |

### CI (`./.github/workflows`)

- `ci.yml`: job `unit-and-architecture` (`mvn test`, Ubuntu, JDK 21 Temurin) em push para `main` e PRs; job `integration-postgresql` (`mvn verify -Pintegration`).
- `qodana_code_quality.yml`: Qodana (linter JVM), configuração em `qodana.yaml`.

### Como rodar

1. `cp .env.example .env` (variáveis: `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`, `SERVER_PORT`, `API_PROXY_TARGET`, `QSOFT_TENANT_SLUG`, `SPRING_API_URL`, `PORT`, `IP`, `JWT_SECRET`).
2. Backend: `./scripts/run-local.ps1` (Windows) / `.sh` (Linux/macOS) ou IDE → `GovernanceApplication`; ou `docker compose up --build` (PostgreSQL 18.6 + backend).
3. Frontend BFF: `npm install && npm start` (http://localhost:3000).
4. Health: `http://localhost:8080/actuator/health`; Swagger UI: `/swagger-ui.html`.

---

## 10. Documentação canônica existente

- `AGENTS.md` — regras de processo/arquitetura (fonte obrigatória).
- `ai/context-map.md` — mapa de contexto progressivo.
- `ai/project.md`, `ai/architecture.md`, `ai/contracts.md`, `ai/dependencies.md`, `ai/testing.md`, `ai/decisions.md`.
- `ai/adr/` — 31 ADRs ACCEPTED + `ADR-000-template.md` (índice em `ai/decisions.md`).
- `ai/epics/` — EPIC-001 (Core/Acessos) a EPIC-005 (Governance Score); `ai/tasks/` só tem `README.md` (sem task packets).
- `docs/development/` — `BOOTSTRAP.md`, `ENVIRONMENT.md`, `HOW_TO_RUN.md`.
- `docs/reference/` — arquitetura funcional (md+docx), C4 (md+docx), `sprint1_sm_review.md`, `README.md`.
- `docs/lessonslearned&Decisions.md` — auditoria da Sprint 1.
- `README.md` — visão geral e baseline.

---

## 11. Estado do Git

- **Branch atual:** `b-ihago` (rastreia `origin/b-ihago`, árvore de trabalho limpa).
- **HEAD:** `6cb5c9f` — "Merge branch 'main' into b-ihago".
- **`origin/main`:** `26bf524` — "Merge pull request #10 from ihagonunes/b-ihago" (main está 1 merge à frente do HEAD local).
- Branches locais: `b-evelyn`, `b-ihago`, `b-ihan`, `main`.
- Branches remotas: `main`, `b-ihago`, `b-ihan`, `IhanDevelopment`, `feature/SPI-22-SPI-27-SPI-36-frontend`.
- Commits recentes de trabalho (main/b-ihago): `feat(iam): fluxo de adicionar registro de usuário`, `chore(SPI-1): remover dependência Flyway redundante`, `feat(SPI-1): dashboard Swagger, provisionamento de usuário e login por e-mail`, `docs(sprint1): adicionar review da Sprint 1`, `feat(SPI-1)` onboarding/JWT/tenant-context, `fix(SPI-1)` flyway/tenancy.
- Convenções (AGENTS.md): branches `<type>/<JIRA-KEY>-descrição-curta`; commits Conventional Commits (`feat|fix|refactor|test|docs|chore|build|ci|perf(<KEY>): descrição`).

---

## 12. Pendências e divergências conhecidas (úteis para planejamento)

1. **Módulos vazios:** department, project, aigateway, chat, agentpolicies, observability, engagement, governancescore são placeholders. Toda feature fora de IAM começa do zero (dentro das fronteiras do módulo e da allowlist).
2. **Endpoint público temporário:** `POST /api/v1/usuarios` (provisionar usuário com senha) está público; o fluxo autorizado (`RegisterUser`/`CreateUser`/`ChangeUserHierarchyLevel`) existe apenas na camada de aplicação, **sem controller REST**. A proteção JWT+permissão no HTTP é trabalho pendente.
3. **Documentação defasada:** `docs/development/HOW_TO_RUN.md` e `docs/lessonslearned&Decisions.md` afirmam que "não há endpoint HTTP de criação de usuário" — **incorreto** desde o commit que adicionou `UserController` (`POST /api/v1/usuarios`).
4. **`scripts/db/create-database.sql` diverge do Flyway** (naming `iam_*` vs `tenant`/`app_user`; banco completo de referência). Não é o schema da aplicação.
5. **Convites/onboarding de senha** (frontend EJS + rotas existem em `admin/convites`, `page admin-convites.js` com mock em localStorage): backend de convite **não existe**.
6. **Departamentos:** frontend de departamentos existe; módulo `department` backend **não existe** (e `app_user.department_id` hoje é ID sem FK nem validação).
7. **Login ponta-a-ponta:** exige `QSOFT_TENANT_SLUG` configurado no BFF (ou slug informado); usuários criados sem `password_hash` não logam.
8. **Segredo JWT:** default em `application.yml` é somente dev; produção deve sobrepor via `JWT_SECRET` (nunca commitar segredo real).
9. **Pendências da Sprint 1 (docs/reference/sprint1_sm_review.md):** SPI-15/16/26 (middleware/isolamento/JWT em todos os endpoints), SPI-28 (tratamento de erros unificado), SPI-29/30 (convites + aceite/senha), SPI-35 (controller de papel), ligação dos frontends ao backend (SPI-22/29-B/36-B) e decisão SPI-21 (admin automático vs convite).
10. **Fora de escopo do MVP (não implementar sem tarefa explícita):** microservices, broker, RAG/pgvector obrigatórios, refresh token, fallback automático de modelo, roteamento por custo/latência, multiagentes, endpoint público `/ai/complete`, provider concreto de IA.

---

## 13. Regras de impacto para novas tasks (Definition of Done aplicável)

- Compilar com Java 21; testes relevantes passando; ArchUnit verde.
- Mudança de persistência → migration Flyway obrigatória; `ddl-auto=validate` deve continuar passando.
- Nenhuma fronteira modular violada (allowlist + `module.api.*` apenas); sem dependência nova sem justificativa em `ai/dependencies.md`/ADR.
- Multi-tenancy: `tenant_id` do contexto autenticado; query tenant-aware com filtro explícito; provar A ≠ B em teste.
- Autorização no backend (permissão/escopo do IAM); nunca confiar no frontend.
- Erros: 4xx explícitos, sem stack trace/segredos; mensagens que não expõem detalhes internos.
- Checks finais: `./scripts/check --changed` → revisar `git diff` → `./scripts/check`; para persistência/multi-tenancy, `./scripts/check-integration`.