# TASK-S3-B1 — Orçamento mensal por departamento (cadastro, limiar e histórico)

## Metadados
- **Onda:** B (orçamento) · **Depende de:** A2 (anotação com `targeting`; pode começar após A1 usando TENANT_ONLY)
- **Jira:** SPI-118, SPI-119, SPI-122, SPI-125 (e parte de SPI-126)
- **Módulo proprietário:** `department`
- **Spec:** `SPEC-02-orcamento.md` §1, §2, §4 (duas primeiras linhas), §6

## Critérios de aceitação
- [ ] `V016__department_monthly_budgets.sql` exatamente como na SPEC-02 §1.
- [ ] Domínio `MonthlyBudget` (sem Spring/JPA), porta e adaptador JPA no `department`.
- [ ] `PUT /api/v1/departamentos/{id}/orcamentos/{mes}`:
  - [ ] upsert de `amount` e `alertThresholdPct`, com `updated_by` = ator;
  - [ ] DEPARTMENT:MANAGE, TO;
  - [ ] `mes` inválido → 400;
  - [ ] depto de outro tenant → 404.
- [ ] `GET /api/v1/departamentos/{id}/orcamentos?de&ate`:
  - [ ] DEPARTMENT:READ, BT;
  - [ ] máximo de 24 meses;
  - [ ] cada mês mostra se veio do valor padrão ou de uma linha própria.
- [ ] `DepartmentBudgetQuery` estendido com `budgetFor` e `budgetsFor` e o record `BudgetLimitInfo`
      (SPEC-02 §2), resolvendo linha do mês > padrão > nenhum.
- [ ] Testes unitários da resolução do mês.
- [ ] `ai/contracts.md` (linha `department`) cita `DepartmentBudgetQuery.budgetFor/budgetsFor` e `BudgetLimitInfo`.
- [ ] ITs:
  - [ ] upsert e histórico;
  - [ ] isolamento de tenant;
  - [ ] RBAC (só TENANT_ADMIN edita; MANAGER(A) lê o histórico de A e recebe 403 em B);
  - [ ] constraints da V016.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
