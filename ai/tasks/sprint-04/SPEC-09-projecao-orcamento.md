# SPEC-09 — Projeção determinística de orçamento (ADR-41, SPI-131)

Tarefa: S4-B1. Módulo `observability` (consome `department.api.DepartmentBudgetQuery`, que já
existe).

## 1. Contrato

`GET /api/v1/observability/orcamentos/projecao?departmentId=&mes=AAAA-MM&janelaDias=`

| Item | Valor |
|---|---|
| Gate | `USAGE:READ`, `BY_TARGET` |
| Checagem fina | `isAllowed(USAGE, READ, DepartmentTarget(departmentId))`; fora → 403; depto de outro tenant → 404 |
| `departmentId` | obrigatório |
| `mes` | padrão: mês corrente (UTC) |
| `janelaDias` | padrão 14; aceita de 7 a 60 (fora → 400) |
| Mês futuro | 400 |
| Mês passado | devolve o realizado: `projetado = gastoAteHoje` do mês inteiro, `diasRestantes = 0` |

```json
{ "departmentId":"…", "mes":"2026-10", "hoje":"2026-10-27", "janelaDias":14,
  "orcamento":1000.00, "orcamentoPadrao":false, "limiarAlerta":80,
  "gastoAteHoje":640.00, "mediaDiaria":28.571429, "diasRestantes":4,
  "projetado":754.29, "pctProjetado":75.43, "diaEsgotamento":null,
  "recomendacao":"DENTRO_DO_ORCAMENTO", "tentativasInconclusivasNaJanela":null,
  "calculationVersion":"projection-v1" }
```

## 2. Regras (`ProjectDepartmentBudget`, sem Spring no cálculo)

- **Datas:** dias civis em UTC. `hoje` = data UTC atual, injetável por `Clock` para os testes.
- **`gastoAteHoje`:** Σ `estimated_cost` do depto de `[1º dia do mês, hoje]`, incluindo hoje.
- **Janela:** `[hoje − janelaDias + 1, hoje]`. Pode atravessar o mês anterior: a média usa a janela,
  não o mês.
- **`mediaDiaria`:** Σ custo da janela / `janelaDias`. Dias sem uso contam como 0. Escala 6, HALF_UP.
- **`diasRestantes`:** dias do mês depois de `hoje`.
- **`projetado`:** `gastoAteHoje + mediaDiaria × diasRestantes`. Escala 2, HALF_UP.
- **`diaEsgotamento`:** a data do primeiro `k ∈ [1, diasRestantes]` em que
  `gastoAteHoje + mediaDiaria × k ≥ orcamento`.
  - Se `gastoAteHoje ≥ orcamento` → `hoje`.
  - Se `mediaDiaria = 0` ou o esgotamento não ocorre no mês → `null`.
- **`recomendacao`:**

  | Condição | Valor |
  |---|---|
  | orçamento ausente (`budgetFor` vazio ou `amount` nulo) | `SEM_ORCAMENTO`, sem exceção; `pct` e `diaEsgotamento` nulos |
  | `gastoAteHoje ≥ orçamento` | `JA_EXCEDIDO` |
  | `projetado ≥ orçamento` | `ESGOTA_NO_MES` |
  | `pctProjetado ≥ limiarAlerta` | `ATENCAO` |
  | demais casos | `DENTRO_DO_ORCAMENTO` |

  Orçamento = 0 → `JA_EXCEDIDO` se houver gasto, senão `DENTRO_DO_ORCAMENTO`, com `pct` nulo.
- **`tentativasInconclusivasNaJanela`:** sempre `null` no observability (allowlist, igual à SPEC-02
  da Sprint 3). O front pode consultar a RF047.

## 3. Front

O card "Projeção do mês" fica no painel de orçamento do dashboard (perfis com `USAGE:READ` no
departamento) e mostra:
- gasto até hoje;
- projetado;
- o dia de esgotamento ou "não esgota neste mês";
- a recomendação em texto. A cor não é a única pista.

## 4. Testes (exatidão — critério da SPI-131)

| ID | Cenário |
|---|---|
| PJ01 | Tabela de casos com `Clock` fixo. Ex.: orçamento 1000, gasto 640, média 28,571429, 4 dias → projetado 754,29; esgotamento nulo |
| PJ02 | Gasto já ≥ orçamento → `JA_EXCEDIDO`, esgotamento = hoje |
| PJ03 | Esgota no dia 29, por média alta |
| PJ04 | Sem orçamento → `SEM_ORCAMENTO`, sem exceção; orçamento 0 com e sem gasto |
| PJ05 | Janela atravessando a virada do mês usa os dias do mês anterior na média, mas não no `gastoAteHoje` |
| PJ06 | Último dia do mês → `diasRestantes = 0`, `projetado = gastoAteHoje` |
| PJ07 | IT RBAC: MANAGER(A) em B → 403; COLLABORATOR → 403; outro tenant → 404 |
| PJ08 | `janelaDias` 6 ou 61 → 400; mês futuro → 400 |
