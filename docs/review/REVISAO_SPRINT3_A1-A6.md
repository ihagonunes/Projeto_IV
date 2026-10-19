# Revisão e correções — Sprint 3, trilha IAM (A1–A6)

- **Data:** 2026-10-19
- **Branch:** `IhanDevelopment`
- **Commits revisados:** `af67bc3` (A1), `a64f080` (A2), `73788e1` (A4), `7edd2ac` (A5), `adcf28f` (A6)
- **Intervalo do diff:** `6d30c60..adcf28f` (113 arquivos, ~4.197 inserções)
- **Método:** revisão independente somente-leitura sobre o diff + verificação em primeira mão dos
  achados + reprodução do achado crítico por teste que falha antes da correção.

## 1. Verificação da suíte

| Momento | Unit + ArchUnit | Integração (Testcontainers) |
|---|---|---|
| Antes da revisão (HEAD `adcf28f`) | 730 ✅ | 98 ✅ |
| Depois das correções | **736 ✅** | **99 ✅** |

Nenhum segredo introduzido; nenhum log de token/senha (grep em `src/main`); fixtures de IT sem
inserção manual de concessão (exceto `RolePersistenceIT`/`RoleAssignmentPersistenceTest`, permitidos).
V015 confere com SPEC-01 §5; `SystemRole` confere com §1 (5/7/13/14/20, `TENANT_ADMIN` sem CHAT).

## 2. Achados e desfecho

| # | Severidade | Achado | Desfecho |
|---|---|---|---|
| F1 | **CRÍTICO** | Dashboard `BY_TARGET` com escopo ainda derivado do **rótulo** (`hierarchyLevel`) → escalonamento de privilégio | **Corrigido** |
| F2 | ALTO | Teste codificava a divergência rótulo × concessão como contrato | **Corrigido** |
| F3 | MÉDIO | Corrida na guarda do último `TENANT_ADMIN` (count-then-act) | **Pendente** (lock pessimista) |
| F4 | MÉDIO | Corrida de concessão duplicada → 500 em vez de 409 | **Corrigido** |
| F5 | MÉDIO | `/me/permissoes` e `/{id}/permissoes` descartavam concessões DEPARTMENT | **Corrigido** |
| F6 | BAIXO | Convite de outro tenant em reenviar/DELETE → 400 (SPEC §8 pede 404) | **Pendente** |
| F7 | BAIXO | Aceite de convite de tenant INACTIVE | **Refutado** (ver §3) |
| F8 | BAIXO | `RoleController` fora do `@RestControllerAdvice` → 500 teórico | **Corrigido** |

## 3. Detalhe dos achados

### F1 — CRÍTICO — Escalonamento de privilégio no dashboard (corrigido)

**Cadeia:** `CreateInvite.resolveLevel` honra o `hierarchyLevel` enviado
(`CreateInvite.java:102-105`) → um admin convida com `roleKey=COLLABORATOR` +
`hierarchyLevel=EXECUTIVE`; `AcceptInvite` cria o usuário com esse rótulo e a concessão
COLLABORATOR/SELF. Em `GET /observability/metrics/usage` e `/costs` (`BY_TARGET`), o gate aceita
qualquer concessão com `USAGE:READ`; em seguida `DashboardScopeResolver` usava
`resolveScope(userId)` (legado), que lê o **rótulo** e devolve `TENANT` → o "colaborador" lia
tokens/custos de todo o tenant.

**Reprodução (teste negativo primeiro):**
`DashboardMetricsIT.collaboratorGrantWithExecutiveLabelSeesOnlyOwnUsage` — antes da correção:
`activeUsers` esperado `1`, obtido `2` (via o uso do admin). Depois: 1.

**Correção:**
- `DashboardScopeResolver` passou a usar
  `AuthorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ)` e mapeia:
  `TenantWide` → filtro opcional pedido; `Departments` → união dos departamentos das concessões
  (pedido ignorado); `SelfOnly` → `userId`; `None` → 403 (`UsageVisibilityDeniedException`).
  A dependência de `IdentityQuery` e a leitura do rótulo foram removidas.
- `UsageMetricsRepository.UsageMetricsFilter`: `departmentId` (UUID) → `departmentIds` (Set),
  permitindo a união; `UsageMetricsJdbcAdapter` gera `department_id IN (?, …)`.
- `ObservabilityApiExceptionHandler`: `UsageVisibilityDeniedException` → 403.

> Observação: isso antecipa a parte do consumidor do dashboard que pertencia à **A3**. A A3
> permanece necessária para `GET /observability/usage` (sem escopo hoje), para os endpoints de
> departamento (`BY_TARGET` + `visibilityFor`) e para DB01–DB04.

### F2 — ALTO — Teste que mascarava F1 (corrigido)

`DashboardMetricsIT.managerShouldSeeOnlyOwnDepartmentEvenRequestingAnother` montava concessão
`EXECUTIVE` com rótulo `MANAGER` e afirmava que o rótulo vencia. Agora usa concessão
`MANAGER/DEPARTMENT(marketing)` coerente e mantém a asserção de escopo; o cenário de exposição
virou o teste negativo acima.

### F3 — MÉDIO — Corrida na guarda do último admin (pendente)

`RevokeRoleAssignment.revoke` faz `countActiveByTenantIdAndRoleId` e depois `save`, sem lock. Duas
revogações simultâneas das duas últimas concessões `TENANT_ADMIN` podem zerar os admins do tenant.
**Correção sugerida:** lock pessimista (`SELECT … FOR UPDATE` no papel `TENANT_ADMIN` do tenant ou
`pg_advisory_xact_lock` por tenant) antes da contagem. Registrar para A7 ou tarefa dedicada.

### F4 — MÉDIO — Corrida de duplicado → 500 (corrigido)

Dois `POST /usuarios/{id}/concessoes` idênticos passam do check em memória e o segundo viola
`uq_iam_ra_active` → `DataIntegrityViolationException` sem handler → 500 (contrato: 409).
Adicionado handler → 409 em `UserApiExceptionHandler` (mensagem genérica, sem revelar constraint) e,
por consistência, no `InviteApiExceptionHandler` (corrida de aceite do mesmo convite viola a
unicidade de e-mail). Testes unitários adicionados nos dois handlers.

### F5 — MÉDIO — Permissões efetivas ignoravam concessões DEPARTMENT (corrigido)

`UserController.myPermissions`/`userPermissions` usavam `EvaluationContext.of(...)`
(departamento `null`); `PolicyEvaluator.applicableRoles` descarta concessões DEPARTMENT quando o
departamento do contexto não casa → MANAGER/LEADER criados pelo fluxo novo (com departamento)
recebiam `{permissoes: []}`, violando SPEC-01 §7.3.
Adicionado `PolicyEvaluator.effectivePermissions(TenantId, UserId)`: união das permissões das
concessões **ativas**, sem filtro de escopo; os dois endpoints passaram a usá-lo. Testes unitários:
concessão DEPARTMENT conta; contexto inválido → vazio.

### F6 — BAIXO — Convite de outro tenant → 400 (pendente)

`ResendInvite`/`RevokeInvite` filtram tenant e lançam `InvalidInviteException` (400). A SPEC §8
formaliza "recurso de outro tenant → 404". Não vaza existência, mas diverge do contrato.
**Correção sugerida:** exceção dedicada → 404, mantendo 400 para token inválido no aceite.

### F7 — BAIXO — Refutado

O aceite de convite de tenant INACTIVE **não** cria usuário: `ProvisionUser` valida tenant ACTIVE
(`TenantNotFoundException` → 404 no `InviteApiExceptionHandler`) e a transação reverte. Não há
criação de usuário/concessão. Resta apenas a divergência de status (404), sem impacto de segurança.

### F8 — BAIXO — `RoleController` sem advice (corrigido)

`UserApiExceptionHandler` agora cobre `{UserController, RoleController}`; `UserNotFoundException`
teórica em `GET /papeis` deixa de virar 500.

## 4. Endpoints `BY_TARGET` após a correção

| Endpoint | Checagem fina |
|---|---|
| `GET /observability/metrics/usage` e `/costs` | `visibilityFor(USAGE:READ)` → filtro por conjunto de departamentos/usuário |
| `GET /convites`, `POST /convites`, `.../reenviar`, `DELETE /convites/{id}` | `GrantAuthority.canGrantByInvite` no caso de uso |
| `GET /papeis` | apenas leitura + `concedivel` calculado por concessão |

Os demais endpoints anotados seguem `TENANT_ONLY` (A3/A7 marcarão os que ganharem checagem fina).

## 5. Arquivos alterados nesta correção

- `observability/application/DashboardScopeResolver.java` (+ `UsageVisibilityDeniedException`)
- `observability/application/port/UsageMetricsRepository.java` (`departmentIds`)
- `observability/infrastructure/query/UsageMetricsJdbcAdapter.java` (`IN` list)
- `observability/infrastructure/web/ObservabilityApiExceptionHandler.java` (403)
- `iam/application/rbac/PolicyEvaluator.java` (`effectivePermissions(TenantId, UserId)`)
- `iam/infrastructure/web/UserController.java` (`/me/permissoes`, `/{id}/permissoes`)
- `iam/infrastructure/web/{UserApiExceptionHandler,InviteApiExceptionHandler}.java` (409; advice)
- Testes: `DashboardMetricsIT` (teste negativo + gestor coerente), `DashboardScopeResolverTest`,
  `UsageMetricsJdbcAdapterTest`, `QueryUsageMetricsTest`, `QueryCostMetricsTest`,
  `PolicyEvaluatorTest`, `UserApiExceptionHandlerTest`, `InviteApiExceptionHandlerTest`
