# TASK-S4-E1 — Contratos públicos para o engajamento: elegíveis, atividade e vínculos em lote

## Metadados
- **Onda:** E · **Ordem:** 1 · **Depende de:** H1; ADR-40 ACCEPTED
- **Jira:** parte da **SPI-143** (pré-requisito)
- **Módulos:** `iam` (api + infra), `observability` (api + infra)
- **Spec:** `SPEC-06-engajamento.md` §1

## Critérios de aceitação
- [ ] `iam.api.query.IdentityQuery.eligibleUsers(TenantId)`:
  - [ ] devolve só usuários `ACTIVE`;
  - [ ] exclui quem tem **apenas** `TENANT_ADMIN` como concessão ativa.
- [ ] `UserContext` ganha `name`, sem quebrar os callers.
- [ ] `iam.api.query.HistoricalIdentityQuery.employeeIds(TenantId, Set<UserId>)` faz uma consulta
      em lote.
- [ ] `observability.api.query.UsageActivityQuery.activeDays(TenantId, Set<UserId>, LocalDate, LocalDate)`:
  - [ ] SQL nativo com `tenant_id` explícito;
  - [ ] dia UTC;
  - [ ] intervalo inclusivo;
  - [ ] lote de até 5.000 usuários (acima disso, paginar internamente).
- [ ] É o primeiro contrato em `observability/api/`. Respeitar ADR-31: o `api` não importa
      `domain`/`infrastructure`.
- [ ] Testes:
  - [ ] unit dos adaptadores;
  - [ ] IT de isolamento de tenant (outro tenant nunca aparece);
  - [ ] IT da borda de dia UTC (23:59:59Z conta para o dia; 00:00Z para o seguinte).
- [ ] `ai/contracts.md` atualizado (linhas `iam` e `observability`).

## Checks
- `./scripts/check`
- `./scripts/check-integration`
