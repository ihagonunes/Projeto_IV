
# Sprint 1 — Visão Geral

## Escopo da Sprint

Foco: construir a fundação de IAM, multi-tenancy, autenticação e autorização do MVP.

**Total de tasks:** 26 | **Story points estimados:** 105

---

| História de Usuário             | Story Points (Valor) | Tasks relacionadas                             |   Complexidade total das Tasks | Valor/Complexidade | Leitura    |
| ------------------------------- | -------------------: | ---------------------------------------------- | -----------------------------: | -----------------: | ---------- |
| POPROJ-13 — Cadastro de Tenant  |                    11 | SPI-19, SPI-20, SPI-21, SPI-22                 |         3 + 5 + 5 + 5 = **18** |                  0,61 | Avaliar    |
| POPROJ-14 — Convite de Usuários |                    8 | SPI-29, SPI-30, SPI-31, SPI-32                 |         8 + 8 + 3 + 5 = **24** |                  0,33 | Avaliar    |
| POPROJ-15 — Autenticação        |                    6 | SPI-23, SPI-24, SPI-25, SPI-26, SPI-27, SPI-28 | 3 + 5 + 5 + 8 + 5 + 3 = **29** |                  0,21 | Avaliar    |
| POPROJ-16 — Gestão de Papéis    |                    8 | SPI-33, SPI-34, SPI-35, SPI-36                 |         3 + 3 + 5 + 5 = **16** |                  0,5 | Avaliar    |
| POPROJ-51 — Multi-tenancy       |                    16 | SPI-13, SPI-14, SPI-15, SPI-16, SPI-17, SPI-18 | 3 + 5 + 8 + 8 + 5 + 3 = **32** |                  0,5 | Avaliar    |

---

## O que foi entregue (15 tasks — 47 pts)

### Infraestrutura e processo

| Task | Descrição | Complexidade |
|------|-----------|:------------:|
| SPI-7 | Repositório e estrutura de branches | 3 |
| SPI-8 | Dockerfile + docker-compose | 5 |
| SPI-9 | Diagrama C4 Nível 1 | 3 |
| SPI-10 | Diagrama C4 Nível 2 | 5 |
| SPI-11 | ADR: stack de backend | 3 |
| SPI-12 | ADR: multi-tenancy | 3 |
| SPI-37 | Refinamento das stories | 3 |
| SPI-38 | Definition of Done | 3 |

### IAM backend

| Task | Descrição | Complexidade |
|------|-----------|:------------:|
| SPI-13 | Tabela tenant (migration V001 + JPA + domínio + repository + testes) | 3 |
| SPI-14 | tenant_id como FK obrigatória (migration V002 + JPA + filtro Hibernate) | 5 |
| SPI-17 | Validação de tenant_id em operações de escrita | 5 |
| SPI-18 | Testes de isolamento entre tenants (5+ classes de teste) | 5 |
| SPI-19 | Validação de dados de entrada (@NotBlank, @Size, validação programática) | 3 |
| SPI-20 | Endpoint POST /api/v1/tenants (TenantController) | 5 |
| SPI-23 | Hashing de senha com BCrypt (BCryptPasswordEncoder) | 3 |
| SPI-24 | Endpoint POST /api/v1/auth/login (AuthController) | 5 |
| SPI-25 | Geração de JWT (JwtTokenIssuer, sub/tenant_id/iat/exp) | 5 |
| SPI-33 | Modelo de 4 níveis hierárquicos (HierarchyLevel enum) | 3 |
| SPI-34 | Flag de permissão administrativa separada (isAdmin via Permission set) | 3 |

### Frontend

| Task | Descrição | Complexidade |
|------|-----------|:------------:|
| SPI-26 | Tela de login (login.ejs + auth.js + api-client.js) | 8 |
| SPI-36 | Tela de gestão de papéis (admin-usuarios.ejs — UI completa) | 5 |

### Migrações Flyway

| Migration | Descrição |
|-----------|-----------|
| V001 | Tabela `tenant` |
| V002 | Tabela `app_user` com FK para `tenant` |
| V003 | Coluna `password_hash` em `app_user` |
| V004 | Tabela `user_permission` (permissões por usuário) |

### Testes (13 arquivos)

- **Unitários:** RegisterTenantTest, CreateUserTest, AuthenticateUserTest, ChangeUserHierarchyLevelTest, IdentityQueryTest, JwtTokenIssuerTest, TenantContextFilterTest
- **Integração:** TenantPersistenceIT, UserPersistenceIT, AuthenticationIT, AuthorizationIT, PostgreSqlSmokeIT
- **Arquitetura:** ArchitectureRulesTest (ArchUnit)

---

## O que ficou em andamento (7 tasks — 45 pts)

Estas tasks estão em desenvolvimento, code review ou pull request. Serão movidas para o backlog da Sprint 2.

| Task | Descrição | Complexidade | O que existe | O que falta |
|------|-----------|:------------:|-------------|-------------|
| **SPI-15** | Middleware que extrai tenant_id do contexto autenticado | 8 | `TenantContextFilter` extrai tenant do JWT; `TenantContextHolder` (ThreadLocal) propaga o contexto | Integração completa com todos os endpoints; validação de cases |
| **SPI-16** | Filtro obrigatório de tenant_id em queries de leitura | 8 | `@Filter` Hibernate no `UserJpaEntity`; `UserRepositoryAdapter` filtra por tenant | Extender para todos os módulos quando existirem; garantir cobertura em queries nativas |
| **SPI-26** | Middleware de verificação de token JWT nas rotas protegidas | 8 | Spring Security OAuth2 Resource Server configurado; `TenantContextFilter` revalida após JWT | Garantir que todas as rotas de negócio passam pela validação; tratar token expirado/inválido |
| **SPI-28** | Tratamento de erros (credenciais inválidas, conta bloqueada) | 3 | `AuthExceptionHandler` existe; `TenantApiExceptionHandler` existe | Tratamento unificado e consistente; mensagens que não permitem enumeração de usuários |
| **SPI-29** | Endpoint de convite (gera token/link) | 5 | Frontend `convites.ejs` existe com UI completa; BFF proxy configurado | Nenhum endpoint backend `/api/v1/convites`; nenhum domínio de convite modelado |
| **SPI-30** | Fluxo de aceite do convite + definição de senha | 8 | — | Nenhum backend; fluxo completo a implementar |
| **SPI-35** | Endpoint de atribuição/alteração de papel | 5 | `ChangeUserHierarchyLevel` (caso de uso + testes) existe | Nenhum controller REST para o endpoint; integração com autorização |

---

## O que não foi iniciado (4 tasks — 13 pts)

Estas tasks não tiveram início e vão para o backlog da Sprint 2.

| Task | Descrição | Complexidade | Dependência |
|------|-----------|:------------:|-------------|
| **SPI-21** | Criação automática do usuário administrador inicial | 5 | SPI-20 (feito), SPI-24 (feito), SPI-29 (pendente — convite é alternativa ao provisioning automático) |
| **SPI-22** | Tela de cadastro de tenant (frontend conectado ao backend) | 5 | `tenant.ejs` existe mas `step-tenant.js` está vazio; BFF proxy configurado mas não utilizado |
| **SPI-29-B** | Backend de convites (admin-convites.js usa localStorage mock) | 3 | SPI-29 (pendente) |
| **SPI-36-B** | Backend de gestão de papéis (admin-usuarios.js usa mock) | 5 | SPI-35 (pendente) |

---

## Resumo financeiro da sprint

| Categoria | Tasks | Story Points | Status |
|-----------|:-----:|:------------:|--------|
| Entregue | 15 | 47 | Feito |
| Em andamento | 7 | 45 | Sprint 2 |
| Não iniciado | 4 | 13 | Sprint 2 |
| **Total** | **26** | **105** | |

**Taxa de conclusão:** 45% das tasks, 45% dos story points.

---

## Lições aprendidas

1. **Fundação IAM sólida:** O módulo IAM ficou robusto com domain, application, infrastructure, testes e security bem separados. A arquitetura em camadas provou ser sustentável.
2. **Frontend avançou rápido:** As telas de login, convites e gestão de papéis ficaram visualmente completas, mas sem backend. Isso cria uma dependência clara para a Sprint 2.
3. **Migrações Flyway funcionaram bem:** 4 migrations incrementais, validação pelo Hibernate, zero conflitos.
4. **Banco do pg_dump diverge do Flyway:** O dump criado em `scripts/db/create-database.sql` usa nomes de tabela diferentes (`iam_tenants`/`iam_users` vs `tenant`/`app_user`). Precisa ser resolvido antes de crescer.
5. **Testes de isolamento cobrem bem:** 5+ classes testam Tenant A ≠ Tenant B em diferentes camadas (unit, integration, arch).

---

## Prioridades para Sprint 2

1. **Completar SPI-15, SPI-16, SPI-26** — Middleware de tenant e JWT é fundação para tudo mais
2. **Completar SPI-29 e SPI-30** — Fluxo de convites é prerequisite para onboarding de usuários
3. **Completar SPI-35** — Endpoint de alteração de papel precisa de controller REST
4. **Conectar frontends ao backend** — SPI-22 (tenant registration), SPI-29-B (convites), SPI-36-B (gestão de papéis)
5. **Resolver divergência Flyway vs pg_dump** — Alinhar nomes de tabela antes de adicionar novas migrations
6. **SPI-21** — Decidir entre provisioning automático do admin OU fluxo de convite (são alternativas)

---
