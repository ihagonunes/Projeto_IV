# ADR-06 — Budget Ownership

## Status

ACCEPTED

## Contexto

Orçamento é meta organizacional; custo realizado é fato de observabilidade.

## Decisão

Department é fonte de verdade de BudgetLimit mensal. Observability é dono de ActualCost, BudgetStatus e Alert e consulta o orçamento por port.

## Alternativas consideradas

Budget dentro de Observability; cópia local de orçamento nos dois módulos.

## Consequências

Separa meta de realizado e evita duas fontes de verdade. Cálculo analítico depende de integração síncrona clara.

## Emenda (Sprint 4)

A projeção de orçamento é um caso de uso determinístico do `observability`
(`ProjectDepartmentBudget`, `projection-v1`), conforme o ADR-41. O orçamento
continua vindo de `department.api.DepartmentBudgetQuery`; a soma do custo
conhecido usa os `UsageRecord` do próprio `observability`, com `tenant_id`
explícito. O cálculo não usa LLM, não altera o orçamento e não cria tabela nova.
`GET /api/v1/observability/orcamentos/projecao` expõe o resultado com
`USAGE:READ` e checagem fina por `DepartmentTarget`.

## Emenda SPI-121 — edição mensal pelo executivo

A edição do orçamento de um departamento por mês exige a permissão
`DEPARTMENT_BUDGET:MANAGE`, atribuída ao papel `EXECUTIVE` no escopo do
próprio tenant. As consultas analíticas continuam sujeitas às permissões
de leitura de cada perfil. A edição do orçamento mensal é separada da
gestão de cadastro de departamentos.

## Emenda LOCAL-BUDGET-001

O ADR-43 acrescenta orçamento empresarial mensal em BRL, distribuição percentual
por departamento e limites financeiros individuais bloqueantes. Department
mantém o livro de reservas e liquidações consultado pelo Gateway. Os dados
legados de orçamento e custo continuam históricos; a tela de edição encaminha
para o orçamento empresarial. O alerta individual usa o limiar empresarial
definido pelo gestor, enquanto o executivo distribui os limites.
