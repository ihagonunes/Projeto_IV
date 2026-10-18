# TASK-S3-A3 — Dashboard, consulta de uso e departamentos sobre `VisibilityScope`

## Metadados
- **Onda:** A · **Ordem:** 3 · **Depende de:** A2 (pode correr em paralelo com A4/A5)
- **Jira:**
  - SPI-94 (manter, redefinir)
  - SPI-112 (fechar como duplicata)
  - SPI-73 (só fecha com esta tarefa)
  - SPI-96 (redefinir para DB01–DB04)
- **Módulos:** `observability`, `department` (consomem `iam.api`)
- **Spec:** `SPEC-01` §6 (linhas de departamentos e observability), §8, §11 (AZ20, DB01–DB04)

## Objetivo
Fechar dois vetores (`VERIFICACAO_FASE0.md`):
- **D4:** depto nulo dá visão do tenant inteiro;
- **D5:** `/observability/usage` não aplica escopo.

Toda consulta agregada passa a usar `visibilityFor`. Filtro pedido fora do escopo → 403
(decisão 10).

## Critérios de aceitação
- [ ] `DashboardScopeResolver` usa `AuthorizationQuery.visibilityFor(USAGE:READ)`. Não usa
      `resolveScope` nem `IdentityQuery` para escopo.
- [ ] `UsageMetricsFilter` e `UsageRecordFilter` carregam o `VisibilityScope` e os filtros pedidos.
- [ ] SQL/Specification por tipo de visibilidade, sempre com `tenant_id` explícito:
  - [ ] TenantWide: sem cláusula extra;
  - [ ] Departments: `(department_id IN (…) OR user_id = ?)`;
  - [ ] SelfOnly: `user_id = ?`;
  - [ ] None: 403 antes da query.
- [ ] `departmentId` ou `userId` pedido fora do escopo → 403 (nova exceção do observability, mapeada
      no handler).
- [ ] `/metrics/usage`, `/metrics/costs` e `/observability/usage` anotados com `BY_TARGET`.
- [ ] `DepartmentController`, ambos `BY_TARGET`:
  - [ ] `GET` (lista) filtrado pela visibilidade de `DEPARTMENT:READ`;
  - [ ] `GET /{id}` com `isAllowed(DepartmentTarget)`.
- [ ] ITs DB01, DB02, DB03, DB04 e AZ20.
  - Usar fixtures pelo fluxo de produção (`support/TestTenants`) se A4/A5 já estiverem no branch.
  - Senão, usar o helper provisório e registrar a dívida no relatório; a A4 migra.
- [ ] Teste unitário de `DashboardScopeResolver` reescrito, sem mock de `resolveScope`.

## Contexto para o agente
- ADR-36
- `SPEC-01`
- `VERIFICACAO_FASE0.md` (D4, D5)
- Código:
  - `observability/application/*`
  - `observability/infrastructure/{query,persistence,web}/*`
  - `department/infrastructure/web/DepartmentController.java`
  - `department/application/ListDepartments.java`

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
