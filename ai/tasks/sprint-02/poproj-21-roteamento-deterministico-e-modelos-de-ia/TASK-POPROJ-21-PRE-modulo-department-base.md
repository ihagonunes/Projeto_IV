# TASK-POPROJ-21-PRE — Módulo `department` (base organizacional)

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** `POPROJ-21` (pré-requisito — ver *Contexto*)
- **História PO:** POPROJ-21 — Roteamento Determinístico e Modelos de IA
- **Status:** Aprovado — implementação em andamento
- **Responsável:** Não atribuído
- **Prioridade:** Alta
- **Categorias:** `sprint-2`, `department`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Contexto

Este task packet **não existe no backlog original da Sprint 2**. Ele foi identificado durante a revisão: o módulo `department` é **dono de estruturas organizacionais** usadas por três blocos da sprint, mas **não tinha task atribuída** — o planejamento assumiu que ele já existiria.

Dependências que **exigem** o módulo:

| Task | Dependência |
|---|---|
| `SPI-80` | schema de permissão **modelo × departamento** |
| `SPI-82` | configuração de **departamentos por modelo** |
| `SPI-73` / `SPI-89` / `SPI-91` | consulta/agregação e **filtro por departamento** |

Sem `department` não há "modelo permitido por departamento" (POPROJ-21), "custo por departamento" (POPROJ-23) nem filtro de departamento no dashboard (POPROJ-53).

**Decisão de escopo:** tratar como **pré-requisito do POPROJ-21**, com a chave `POPROJ-21` nos commits, e recortar o módulo ao mínimo que desbloqueia a sprint.

## Área proprietária

`department`

## Escopo INCLUÍDO (v1)

- **`Department`** — agregado raiz do módulo: `id`, `tenantId`, `name`, `description`, `leaderUserId` (referência cross-module por ID, sem FK — ADR-29), `monthlyBudget` (ADR-06), `status`, timestamps.
- **`BudgetLimit`** — representado como atributo do departamento (`monthlyBudget`). ADR-06: *"Department é fonte de verdade de BudgetLimit mensal"*.
- **CRUD REST** — `GET/POST /api/v1/departamentos`, `GET/PUT/DELETE /api/v1/departamentos/{id}` (contrato já esperado pelo frontend em `static/modules/departamentos/departamentos.controller.js`).
- **Contratos públicos** (`department/api/query/`), consumidos por outros módulos via `module.api.*`:
  - `OrganizationQuery` — dados organizacionais por `departmentId` (id, tenantId, name).
  - `DepartmentBudgetQuery` — orçamento mensal por `departmentId` (para o POPROJ-23).
- **RBAC** — `DEPARTMENT:READ` (leitura) e `DEPARTMENT:MANAGE` (escrita), **já existentes na matriz padrão** (LEADER+ lê; EXECUTIVE gerencia).
- **Isolamento de tenant** — `tenantId` sempre do contexto autenticado; toda query filtra por `tenant_id` explicitamente.

## Escopo EXCLUÍDO (adiado, com justificativa)

| Item | Motivo |
|---|---|
| `Area` + `UserAreaMembership` (ADR-05) | Sem UI no frontend e não exigidos por POPROJ-21/23/53. |
| `ModelPolicy` (ADR-07) | Pertence ao **POPROJ-21** (SPI-80/82 são exatamente esse schema e sua configuração). |
| Alertas (`limiteAlerta`, `emailAlerta`) | **ADR-06**: `Alert`/`BudgetStatus` pertencem ao **observability** (POPROJ-23). |
| Orçamento com histórico/versionamento | Complexidade sem valor imediato; um valor vigente por departamento basta. |

## Modelo de dados (migration `V009`)

```sql
CREATE TABLE department_departments (
    id             UUID          NOT NULL,
    tenant_id      UUID          NOT NULL,
    name           VARCHAR(150)  NOT NULL,
    description    VARCHAR(500),
    leader_user_id UUID,
    monthly_budget NUMERIC(15,2),
    status         VARCHAR(20)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_department_departments PRIMARY KEY (id),
    CONSTRAINT uq_department_departments_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_department_departments_tenant ON department_departments (tenant_id);
```

Notas:
- **Sem FK para `iam_tenants`**: `tenant_id` é referência **cross-module por ID** (o agregado `Tenant` pertence ao módulo `iam`). O ADR-29 é explícito — *"FKs apenas intramodule; cross-module por IDs/ports"* — portanto aqui **não há** `FOREIGN KEY`. O isolamento é garantido pela aplicação (predicado explícito por `tenant_id`) + índice.
- `leader_user_id` também é referência cross-module **por ID, sem FK** (ADR-29).
- `UNIQUE (tenant_id, name)` — nome único **dentro do tenant**.
- `status` (`ACTIVE`/`INACTIVE`) para **remoção lógica** (ver *Decisões tomadas*).

## API

| Método | Caminho | Corpo | Resposta | Autorização |
|---|---|---|---|---|
| GET | `/api/v1/departamentos` | — (filtros opcionais: `search`, `status`) | lista de departamentos | `DEPARTMENT:READ` |
| POST | `/api/v1/departamentos` | `{ name, description?, leaderUserId?, monthlyBudget? }` | 201 + departamento | `DEPARTMENT:MANAGE` |
| GET | `/api/v1/departamentos/{id}` | — | departamento | `DEPARTMENT:READ` |
| PUT | `/api/v1/departamentos/{id}` | `{ name, description?, leaderUserId?, monthlyBudget? }` | departamento | `DEPARTMENT:MANAGE` |
| DELETE | `/api/v1/departamentos/{id}` | — | 204 | `DEPARTMENT:MANAGE` |

Erros: `400` validação · `401` sem autenticação · `403` sem permissão · `404` inexistente/no outro tenant · `409` nome duplicado no tenant.

## Estrutura do módulo (padrão ADR-31)

```text
department/
├── api/query/          OrganizationQuery, DepartmentBudgetQuery (+ DTOs em api/model)
├── domain/             Department (agregado), DepartmentStatus
├── application/        CreateDepartment, UpdateDepartment, DeleteDepartment,
│   │                   GetDepartment, ListDepartments, *Command
│   └── port/           DepartmentRepository
└── infrastructure/
    ├── persistence/    DepartmentJpaEntity, DepartmentJpaMapper, DepartmentJpaRepository,
    │                   DepartmentRepositoryAdapter
    ├── query/          OrganizationQueryAdapter, DepartmentBudgetQueryAdapter
    └── web/            DepartmentController, DepartmentRequest/Response, DepartmentApiExceptionHandler
```

## Multi-tenancy

- `tenantId` vem **sempre** do `TenantContext` (nunca do cliente).
- Predicado explícito por `tenant_id` em toda consulta (defesa primária) + filtro Hibernate quando aplicável (defesa secundária), conforme ADR-03.
- Teste de integração obrigatório com **dois tenants** (A não vê B).

## Testes

- **Unitários** (sem Spring/banco): `CreateDepartmentTest` (nome obrigatório, duplicado), `UpdateDepartmentTest`, `DeleteDepartmentTest`, `DepartmentTest` (invariantes do agregado).
- **Integração** (PostgreSQL real): `DepartmentManagementIT` — CRUD ponta a ponta, **isolamento de tenant**, **autorização** (403 sem `DEPARTMENT:MANAGE`), **409** de nome duplicado, **404** cross-tenant.
- **ArchUnit** deve continuar verde: nenhum import interno cross-module; `department` só pode depender de `sharedkernel` e `iam`.

## Plano de implementação (ordem)

1. Migration `V009` + agregado `Department` + `DepartmentStatus`.
2. Porta `DepartmentRepository` + persistência (entity/mapper/repository/adapter).
3. Casos de uso (`Create/Update/Delete/Get/List` + commands).
4. Contratos públicos `OrganizationQuery` e `DepartmentBudgetQuery` + adapters.
5. Web: `DepartmentController` + DTOs + exception handler + `@RequiresPermission`.
6. Testes unitários e de integração.
7. `./scripts/check` e `./scripts/check-integration` verdes; revisão do diff.
8. Atualizar `ai/architecture.md` (ownership/allowlist de `department`) se necessário.

## Decisões tomadas (aprovadas)

1. **Remoção:** `DELETE` é **remoção lógica** — `status = INACTIVE`. Evita referências órfãs em `app_user.department_id` (cross-module por ID, sem FK). A listagem padrão retorna apenas `ACTIVE`; `?status=INACTIVE` permite ver os removidos.
2. **Nome da tabela:** `department_departments` (padrão `<módulo>_<agregado>`, como `iam_tenants`).
3. **`monthlyBudget`:** **opcional** (`NUMERIC(15,2)` anulável) — nem todo departamento tem teto definido no MVP.
4. **FK de `tenant_id`:** **não criar** — referência cross-module por ID (ADR-29).

## Regras transversais

- Cross-module somente via `module.api.*`.
- Sem associação JPA/FK SQL cross-module.
- TenantId vem do contexto autenticado.
- Persistência muda somente com Flyway.
- PostgreSQL é o único banco suportado.
- Frontend nunca substitui autorização backend.
- Mudança estrutural exige ADR.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
