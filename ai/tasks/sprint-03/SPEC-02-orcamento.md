# SPEC-02 — Orçamento por departamento com alerta (POPROJ-54)

Base: ADR-06. O `department` é dono do `BudgetLimit` e o `observability` é dono de `ActualCost`,
`BudgetStatus` e `Alert`. Regra já decidida: o orçamento **alerta, não bloqueia** (A105). Limiares:
80% e 100% (EPIC-003).

## 1. Modelo e migration V016 (`V016__department_monthly_budgets.sql`, módulo `department`)

```sql
CREATE TABLE department_monthly_budgets (
    id                  UUID          NOT NULL,
    tenant_id           UUID          NOT NULL,
    department_id       UUID          NOT NULL,
    month               DATE          NOT NULL,     -- sempre o dia 1 do mês
    amount              NUMERIC(15,2) NOT NULL,
    alert_threshold_pct SMALLINT      NOT NULL DEFAULT 80,
    updated_by          UUID          NOT NULL,     -- usuário do iam (ID, sem FK cross-module)
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_department_monthly_budgets PRIMARY KEY (id),
    CONSTRAINT uq_department_monthly_budgets UNIQUE (tenant_id, department_id, month),
    CONSTRAINT fk_dmb_department FOREIGN KEY (department_id) REFERENCES department_departments (id),
    CONSTRAINT ck_dmb_amount    CHECK (amount >= 0),
    CONSTRAINT ck_dmb_threshold CHECK (alert_threshold_pct BETWEEN 1 AND 100),
    CONSTRAINT ck_dmb_month     CHECK (EXTRACT(DAY FROM month) = 1)
);
CREATE INDEX idx_dmb_tenant_month ON department_monthly_budgets (tenant_id, month);
```

- O `department_departments.monthly_budget` existente passa a ser o **orçamento padrão**: vale
  para um mês sem linha própria. Não há backfill.
- O orçamento de um mês é resolvido nesta ordem:
  1. a linha do mês;
  2. o `monthly_budget` padrão do departamento, com limiar 80;
  3. "sem orçamento" (nunca zero).
- Meses são calculados em **UTC**, como no restante do observability.

## 2. Contratos

- `department.api.query.DepartmentBudgetQuery` (estendido):

  ```java
  Optional<BudgetLimitInfo> budgetFor(TenantId t, UUID departmentId, YearMonth month);
  List<BudgetLimitInfo> budgetsFor(TenantId t, YearMonth month);            // todos os deptos ATIVOS
  record BudgetLimitInfo(UUID departmentId, String departmentName, YearMonth month,
                         BigDecimal amount, int alertThresholdPct, boolean fromDefault) {}
  ```

  `BudgetLimitInfo` fica em `department.api.model`. `amount == null` significa "sem orçamento"; o
  departamento entra na lista mesmo assim.
- O `observability` ganha `BudgetStatusQueryPort` (`ai/contracts.md` já o prevê) e o caso de uso
  `QueryBudgetStatus`.

## 3. Regras (`observability.application`)

```
custoReal(dept, mês) = Σ estimated_cost de observability_usage_records
                        WHERE tenant_id = ? AND department_id = ? AND created_at ∈ [início, fim)
pctUsado = amount > 0 ? custoReal / amount × 100 (2 casas, HALF_UP) : null
status:
  amount == null                                  → SEM_ORCAMENTO
  amount == 0 && custoReal == 0                   → OK
  amount == 0 && custoReal > 0                    → EXCEDIDO
  pctUsado >= 100                                 → EXCEDIDO
  pctUsado >= alertThresholdPct                   → ALERTA
  senão                                           → OK
```

- Alertas **não são persistidos**: o status é calculado na leitura (0 eventos e 0 tabelas novas no
  observability).
- Custos `INCONCLUSIVE` (Onda C) nunca chegam ao `UsageRecord`, então não entram na soma.
- O campo `tentativasInconclusivas` **não** é preenchido pelo observability: a allowlist proíbe
  `observability → aigateway`. O front obtém o número pela RF047 (TASK-S3-C3).

## 4. Endpoints

| Rota | Permissão | Targeting | Regra |
|---|---|---|---|
| `PUT /api/v1/departamentos/{id}/orcamentos/{mes}` (`mes` = `AAAA-MM`) | DEPARTMENT_BUDGET:MANAGE | BT | upsert de `amount` e `alertThresholdPct`; `updated_by` = ator; depto de outro tenant → 404; `mes` inválido → 400 |
| `GET /api/v1/departamentos/{id}/orcamentos?de=AAAA-MM&ate=AAAA-MM` | DEPARTMENT:READ | BT | histórico (SPI-125); `isAllowed(DepartmentTarget(id))`; máximo 24 meses |
| `GET /api/v1/observability/orcamentos?mes=AAAA-MM` | USAGE:READ | BT | `visibilityFor(USAGE:READ)`: TenantWide → todos os deptos; Departments → só os ids; SelfOnly → 403 |

Resposta do status:

```json
{ "mes":"2026-10", "itens":[{ "departmentId":"…", "departmentName":"Marketing",
  "orcamento":1000.00, "custoReal":850.40, "pctUsado":85.04, "limiarAlerta":80,
  "status":"ALERTA", "orcamentoPadrao":false }] }
```

## 5. Front

- **SPI-121**: tela de administração de orçamento no módulo `departamentos`:
  - lista os departamentos e o orçamento do mês;
  - edita o valor e o limiar;
  - mostra o histórico.
- **SPI-124**: card no dashboard com o status por departamento (cores OK, ALERTA, EXCEDIDO) e
  "Sem orçamento" quando não houver.
- O front esconde a edição para quem não tem `DEPARTMENT_BUDGET:MANAGE` (via `/me/permissoes`). O backend
  continua decidindo.

## 6. Testes (SPI-126)

- **Unit** do cálculo: 79,99% → OK; 80% → ALERTA; 99,99% → ALERTA; 100% → EXCEDIDO; amount 0 com
  e sem custo; sem orçamento; limiar customizado 50%; arredondamento.
- **Unit** da resolução do mês: linha própria > padrão > nenhum.
- **IT**: upsert e histórico; isolamento de tenant (404); MANAGER(A) vê só A; COLLABORATOR → 403;
  custo de um mês não vaza para o seguinte (limites de mês em UTC).
- **IT de Flyway** da V016: constraints (dia ≠ 1, amount negativo, limiar 0 → rejeitados).
