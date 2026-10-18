# TASK-S3-A2 — Avaliador com alvo, visibilidade e gate fechado por padrão

## Metadados
- **Onda:** A · **Ordem:** 2 · **Depende de:** A1
- **Jira:** SPI-42 (redefinir — parte de avaliação)
- **Módulo proprietário:** `iam`
- **Spec:** `SPEC-01` §2.2, §3, §6 (coluna Targeting), §11 (AZ10, AZ12 unit, AZ19 IT)

## Objetivo
Implementar no `PolicyEvaluator`:
- `isAllowed`;
- `visibilityFor`;
- os gates TENANT_ONLY e BY_TARGET.

Expor `isAllowed` e `visibilityFor` em `AuthorizationQuery` e fazer o `PermissionInterceptor`
respeitar o `targeting`.

## Fora de escopo
- Migrar consumidores do observability e do department (A3).
- Guardas de usuários (A7).
- Remover `checkPermission`/`resolveScope` (A8). Nesta tarefa eles **continuam existindo**, para não
  quebrar os consumidores.

## Critérios de aceitação
- [ ] `iam.api.rbac.Targeting` criado; `@RequiresPermission` ganha `targeting`, com `TENANT_ONLY`
      por padrão.
- [ ] `AuthorizationQuery` ganha `isAllowed(...)` e `visibilityFor(...)` (§2.2).
- [ ] `checkPermission` e `resolveScope` marcados com `@Deprecated(forRemoval = true)`.
- [ ] `PolicyEvaluator` cumpre a §3 inteira:
  - [ ] só concessões ativas;
  - [ ] papel do mesmo tenant;
  - [ ] teto CHAT = SELF;
  - [ ] sem cache.
- [ ] `PermissionInterceptor`:
  - [ ] TENANT_ONLY exige concessão com escopo TENANT;
  - [ ] BY_TARGET exige qualquer concessão ativa que contenha a permissão.
- [ ] Endpoints marcados como BT na §6:
  - [ ] só recebem `BY_TARGET` se a checagem fina já existir no caso de uso;
  - [ ] os demais ficam com o padrão TENANT_ONLY (mais restrito);
  - [ ] o relatório lista os endpoints que ficaram temporariamente mais restritos (A3 e A7
        liberam).
- [ ] Testes unitários:
  - [ ] AZ10;
  - [ ] AZ12: EXECUTIVE e TENANT_ADMIN não leem CHAT de outro;
  - [ ] visibilidade None, SelfOnly, Departments e TenantWide;
  - [ ] união de 2 concessões;
  - [ ] concessão revogada é ignorada;
  - [ ] papel de outro tenant é ignorado.
- [ ] IT AZ19: concessão DEPARTMENT em endpoint TENANT_ONLY → 403.

## Contexto para o agente
- ADR-34, ADR-36
- `SPEC-01`
- Código: `PolicyEvaluator`, `PermissionInterceptor`, `AuthorizationQueryAdapter`,
  `RequiresPermission`

## Arquivos prováveis
- `iam/api/rbac/{RequiresPermission,Targeting}.java`
- `iam/api/query/AuthorizationQuery.java`
- `iam/application/rbac/{PolicyEvaluator,EvaluationContext}.java`
- `iam/infrastructure/web/PermissionInterceptor.java`
- `iam/infrastructure/query/AuthorizationQueryAdapter.java`
- testes correspondentes

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
