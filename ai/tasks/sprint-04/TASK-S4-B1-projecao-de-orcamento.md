# TASK-S4-B1 — Projeção determinística de orçamento (Pesquisa Operacional)

## Metadados
- **Onda:** B · **Depende de:** H1; ADR-41 ACCEPTED
- **Jira:** **SPI-131** (redefinida: projeção determinística no `observability`, **não** é tool do
  agente — decisão S4-D6)
- **Módulo:** `observability` (+ card no front do dashboard)
- **Spec:** `SPEC-09-projecao-orcamento.md` (inteira)

## Critérios de aceitação
- [ ] Caso de uso `ProjectDepartmentBudget`:
  - [ ] cálculo puro, testável com `Clock`;
  - [ ] usa o `DepartmentBudgetQuery.budgetFor` existente;
  - [ ] soma nativa com `tenant_id` explícito.
- [ ] `GET /api/v1/observability/orcamentos/projecao`: contrato, validações e RBAC da SPEC-09 §1.
- [ ] Testes PJ01–PJ08. A tabela de casos do PJ01 traz os valores esperados calculados à mão no
      próprio teste.
- [ ] Card "Projeção do mês" no painel de orçamento do dashboard (SPEC-09 §3).
- [ ] `ai/contracts.md` (linha `observability`) e `docs/development/DASHBOARD_E_ORCAMENTOS.md`
      atualizados.
- [ ] Nenhuma chamada a LLM.

## Restrição
Não tocar no componente de chat.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js`
