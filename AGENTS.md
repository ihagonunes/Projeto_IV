# AGENTS.md

## 1. Project Overview

SM_Projeto_IV is a multi-tenant SaaS platform.

The current development focus is the SPI MVP, whose foundation includes:

- multi-tenancy;
- authentication;
- authorization and RBAC;
- users;
- departments;
- roles;
- tenant isolation;
- API;
- database persistence.

The project is developed incrementally through Jira issues associated
with the SPI project.

Always identify the Jira issue being implemented before making changes.

---

## 2. Source of Truth

Before implementing a feature, inspect:

1. the Jira issue and its acceptance criteria;
2. the existing code;
3. the project architecture/documentation;
4. relevant ADRs;
5. existing tests;
6. related implementations.

Do not implement based only on the issue title.

If the requirements are ambiguous, inspect the existing project decisions
before introducing a new interpretation.

Do not silently change architectural decisions to make an implementation
easier.

---

## 3. Architecture

The application follows a layered monolithic architecture.

Keep responsibilities separated between:

- presentation/API;
- application;
- domain;
- infrastructure/persistence.

Business rules belong in the appropriate backend/domain/application layer,
not in controllers or frontend components.

Controllers should coordinate requests and responses rather than contain
complex business logic.

Repositories should handle persistence concerns and should not contain
business rules that belong to the domain/application layer.

Avoid unnecessary coupling between layers.

Do not introduce microservices or distributed infrastructure unless
explicitly required by the project.

---

## 4. Multi-Tenancy

Tenant isolation is a core security requirement.

The system uses logical isolation through `tenant_id`.

Any tenant-owned entity must be associated with the appropriate tenant.

For authenticated requests:

1. identify the tenant from the authenticated context;
2. propagate the tenant context to the application layer;
3. apply tenant isolation to database operations.

Never trust a client-provided `tenant_id` when the tenant can be determined
from the authenticated user/session.

Every tenant-scoped read must prevent access to another tenant's data.

Every tenant-scoped write must validate the tenant context.

When implementing a new entity or endpoint, explicitly determine whether
it is:

- global;
- tenant-scoped;
- user-scoped;
- related to another tenant-scoped entity.

Any implementation that could allow cross-tenant access must be treated
as a security defect.

---

## 5. Authentication

Authentication uses email and password.

Passwords must never be stored in plaintext.

Use the project's configured password hashing mechanism.

After successful authentication, the backend issues a JWT.

Protected endpoints must validate authentication before executing
protected operations.

Never expose:

- passwords;
- password hashes;
- JWT secrets;
- private keys;
- credentials;
- environment secrets.

Never commit `.env` files containing real credentials.

---

## 6. Authorization / RBAC

Authentication determines who the user is.

Authorization determines what the user is allowed to do.

Do not treat frontend visibility as authorization.

Sensitive operations must be authorized by the backend.

When implementing an endpoint, explicitly verify:

- whether authentication is required;
- which role or permission is required;
- whether the operation is tenant-scoped;
- whether the user can operate on the target resource.

Do not duplicate authorization logic unnecessarily across controllers.

Centralize authorization behavior where appropriate.

---

## 7. Database

Database changes must preserve:

- referential integrity;
- tenant isolation;
- uniqueness constraints;
- appropriate indexes;
- valid relationships.

Use migrations for schema changes when supported by the project.

Before creating a new table/entity, inspect existing entities and
relationships.

Do not duplicate information that can be represented through an existing
relationship.

When adding a tenant-owned entity, determine how `tenant_id` is enforced
and indexed.

---

## 8. API Conventions

Endpoints should:

- use appropriate HTTP methods;
- use consistent resource naming;
- validate input;
- return appropriate HTTP status codes;
- return predictable response structures;
- avoid exposing internal implementation details.

Expected client errors should return appropriate 4xx responses.

Unexpected server errors should not expose stack traces, SQL queries,
internal paths, secrets, or other sensitive implementation details.

---

## 9. Error Handling

Handle expected errors explicitly.

Do not use broad exception handling merely to hide failures.

Error responses should provide enough information for the client to
understand the failure without exposing sensitive internal information.

Logs must never contain:

- passwords;
- authentication tokens;
- secrets;
- private keys.

Use the project's existing error-handling conventions before introducing
a new mechanism.

---

## 10. Testing

Every meaningful feature should include appropriate tests.

Prioritize tests for:

- authentication;
- authorization;
- tenant isolation;
- validation;
- business rules;
- critical API endpoints.

For multi-tenant functionality, test both:

### Allowed access

A user can access resources belonging to their tenant.

### Forbidden access

A user cannot access resources belonging to another tenant.

Do not consider a feature complete if its critical security behavior
has not been tested.

---

## 11. Working on a Jira Issue

Before coding:

1. Read the Jira issue.
2. Identify the acceptance criteria.
3. Inspect related code.
4. Identify affected layers.
5. Identify security implications.
6. Identify affected tests.
7. Implement the smallest appropriate change.

While coding:

1. Follow existing project conventions.
2. Avoid unrelated refactoring.
3. Keep the change focused on the Jira issue.
4. Add or update tests.
5. Update documentation when required.

After coding:

1. Run formatting/linting if configured.
2. Run relevant tests.
3. Run the complete test suite when practical.
4. Verify the acceptance criteria.
5. Review the diff for unrelated changes.
6. Check that no secrets were introduced.

---

## 12. Git Conventions

Branches must reference the Jira issue.

Preferred format:

`<type>/<JIRA-KEY>-short-description`

Examples:

`feat/SPI-15-tenant-middleware`

`fix/SPI-24-invalid-login`

Commit messages must follow Conventional Commits.

Format:

`<type>(<JIRA-KEY>): <description>`

Examples:

`feat(SPI-15): implement tenant context middleware`

`fix(SPI-24): handle invalid login credentials`

`test(SPI-18): add tenant isolation tests`

Allowed types:

- feat
- fix
- refactor
- test
- docs
- chore
- build
- ci
- perf

Keep commit messages concise and imperative.

Do not combine unrelated changes into one commit.

---

## 13. Pull Requests

Pull Request titles must follow:

`<type>(<JIRA-KEY>): <short description>`

The Pull Request description must follow:

`.github/pull_request_template.md`

Every PR should:

- reference the Jira issue;
- explain the changes;
- describe how to test;
- address acceptance criteria;
- mention relevant security considerations;
- confirm that CI passes.

Do not create a PR that contains unrelated changes.

---

## 14. Documentation

Architectural decisions must be documented through ADRs when appropriate.

Do not modify architecture silently.

When a change introduces a significant architectural decision,
document:

- context;
- decision;
- alternatives considered;
- consequences.

Keep documentation consistent with the actual implementation.

---

## 15. Dependencies

Before adding a dependency:

1. verify whether the existing project already provides the functionality;
2. check whether an existing dependency can solve the problem;
3. consider security and maintenance implications;
4. avoid adding dependencies for trivial functionality.

Do not introduce libraries solely because they are convenient.

---

## 16. Security Rules

Treat security as a first-class requirement.

Never:

- bypass authentication for convenience;
- trust client-provided tenant information;
- expose secrets;
- commit credentials;
- disable authorization checks to make tests pass;
- expose internal errors to clients;
- weaken tenant isolation;
- disable security tests without a documented reason.

If a requirement conflicts with a security invariant, preserve the
security invariant and flag the conflict.

---

## 17. Minimal Change Principle

Prefer the smallest change that completely satisfies the requirement.

Do not:

- refactor unrelated code;
- rename unrelated classes;
- change project architecture unnecessarily;
- introduce abstractions without a concrete need;
- modify configuration unrelated to the task.

A focused PR is preferable to a broad cleanup.

---

## 18. Definition of Done

A Jira issue is considered technically complete when:

- implementation satisfies the acceptance criteria;
- architecture is respected;
- relevant tests exist and pass;
- authentication/authorization is correct when applicable;
- tenant isolation is correct when applicable;
- errors are handled appropriately;
- no secrets are introduced;
- documentation is updated when necessary;
- CI passes;
- the final diff contains no unrelated changes.

---

## 19. Guia Operacional — Plataforma QSOFT de Governança de IA

Este repositório implementa a Plataforma QSOFT de Governança de IA como
um Monólito Modular Java/Spring Boot. O contexto deve ser carregado
progressivamente: este arquivo → `ai/context-map.md` → contexto específico
→ código.

Regras não negociáveis:

- Não invente requisitos.
- Não altere a arquitetura sem propor/atualizar ADR em `ai/adr/`.
- O projeto é um único Maven, não Maven multi-module.
- PostgreSQL é o único SGBD suportado. Não introduza H2.
- Docker é opcional no desenvolvimento e não define fronteiras de domínio.
- Outro módulo pode importar somente `sharedkernel` e a superfície `api.*`
  de módulos permitidos.
- Não importe `domain`, `application`, `infrastructure`, repository, entity
  ou adapter de outro módulo.
- Não crie FK SQL ou associação JPA cross-module.
- Relações cross-module são IDs + Command/Query Port ou Domain Event.
- `domain` não depende de Spring, JPA, SDK de provider, `application` ou
  `infrastructure`.
- TenantId vem do contexto autenticado; nunca confie em tenant enviado
  pelo frontend.
- Query SQL nativa tenant-aware deve conter filtro explícito por `tenant_id`.
- LLM nunca decide autorização.
- Conteúdo de prompt/resposta/documento/segredo não entra em módulos
  analíticos.
- Não exponha endpoint público `/ai/complete`.
- Não introduza RAG, pgvector, provider concreto, broker, Redis ou
  observabilidade externa sem tarefa explícita.
- Não introduza dependência nova sem justificar em `ai/dependencies.md` ou
  ADR quando arquitetural.
- Toda mudança de comportamento deve ter teste proporcional ao risco.
- Nunca coloque segredo real no repositório.

Arquivos canônicos:

- `ai/project.md` — produto e escopo.
- `ai/architecture.md` — módulos, fronteiras e dependências.
- `ai/contracts.md` — contratos públicos e eventos.
- `ai/testing.md` — estratégia de testes.
- `ai/dependencies.md` — catálogo técnico e política de dependências.
- `ai/decisions.md` — índice dos ADRs.
- `ai/adr/` — decisões formais.
- `ai/epics/` — agrupamentos funcionais.
- `ai/tasks/` — task packets curtos.

Fluxo de implementação:

1. Entender a tarefa.
2. Ler somente o contexto mínimo.
3. Identificar módulo proprietário.
4. Confirmar contratos afetados.
5. Planejar menor mudança reversível.
6. Implementar dentro da fronteira do módulo.
7. Adicionar/ajustar testes.
8. Executar `./scripts/check --changed`.
9. Revisar `git diff`.
10. Executar `./scripts/check`.
11. Para persistência/multi-tenancy, executar também `./scripts/check-integration`.

Definition of Done:

- compila com Java 21;
- testes relevantes passam;
- ArchUnit passa;
- migration Flyway acompanha alteração de persistência;
- nenhuma fronteira modular foi violada;
- nenhuma regra tenant-aware foi enfraquecida;
- documentação canônica foi atualizada se necessário;
- diff contém somente mudanças relacionadas.