# ADR-41 — Projeção determinística de orçamento (Pesquisa Operacional)

## Status

ACCEPTED (2026-10-28), com as recomendações das decisões S4-D2 a S4-D13 (`ai/tasks/sprint-04/INDEX.md` §2).

## Contexto

A SPI-131 pede uma `PredictiveBudgetOptimizationTool`. Ela deve calcular, por departamento, o gasto
médio diário dos últimos N dias, a projeção linear até o fim do mês e o dia estimado de
esgotamento do orçamento, **sem** que o modelo de linguagem faça conta.

A tarefa está no épico do agente (POPROJ-36), mas:

- o orçamento (ADR-06, V016) e o custo real (`observability`) já existem;
- a allowlist proíbe `agentpolicies → observability`;
- o agente de políticas responde só sobre políticas (ADR-35).

## Decisão

1. A projeção é um caso de uso **do `observability`** (`ProjectDepartmentBudget`), puro e
   determinístico, coberto por testes de exatidão. O LLM não participa.
2. **Entradas:** tenant autenticado, `departmentId`, `mes` (padrão: corrente) e `janelaDias`
   (padrão 14; mínimo 7; máximo 60).
   - O custo diário vem dos `UsageRecord` do departamento (custo conhecido).
   - O orçamento vem do `DepartmentBudgetQuery` (linha do mês > padrão > nenhum).
3. **Regra:**
   - `mediaDiaria` = custo conhecido na janela / `janelaDias`, contando dias sem uso como zero;
   - `projetado` = `gastoAtéHoje` + `mediaDiaria` × dias restantes do mês (UTC);
   - `diaEsgotamento` = primeiro dia em que `gastoAtéHoje` + `mediaDiaria` × k ≥ orçamento;
     `null` se não ocorrer no mês ou se a média for 0;
   - `recomendacao` vem de um enum fixo: `DENTRO_DO_ORCAMENTO`, `ATENCAO`, `ESGOTA_NO_MES`,
     `JA_EXCEDIDO`, `SEM_ORCAMENTO`. Os limiares são o limiar de alerta do orçamento (V016) e 100%.
   - Sem orçamento → `SEM_ORCAMENTO`, sem exceção (critério da SPI-131).
4. **Exposição:** `GET /api/v1/observability/orcamentos/projecao`, com `USAGE:READ`, `BY_TARGET` e
   `isAllowed(DepartmentTarget)`. O card aparece no dashboard (orçamento).
5. **Fora do agente nesta sprint.** Expor a projeção como ferramenta do agente exige mudar a
   allowlist (`agentpolicies → observability.api`) e um ADR próprio. Decisão S4-D6: opção (b),
   adiada.

## Alternativas consideradas

| Alternativa | Por que não foi adotada agora |
|---|---|
| Ferramenta do agente (leitura literal do Jira) | Exige mudar a allowlist e amplia o escopo do agente de políticas. |
| Regressão linear por mínimos quadrados | Mais sensível a outliers e mais difícil de explicar na demo. A média móvel (run-rate) é o que o próprio Jira descreve. Pode virar a `projection-v2`. |
| Solver de otimização (PuLP/Python) | Dependência e runtime novos; fora do orçamento (doc de indicadores §6). |

## Consequências

- Um endpoint e um caso de uso no `observability`, sem migration, sem evento e sem dependência
  nova.
- Custos INCONCLUSIVE não entram na média; a resposta traz a cobertura.
- A SPI-131 é **redefinida** de "tool do agente" para "projeção determinística no observability".
