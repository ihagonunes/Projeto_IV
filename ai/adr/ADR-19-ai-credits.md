# ADR-19 — AI Credits

## Status

ACCEPTED

## Contexto

Gamificação não pode incentivar desperdício nem bloquear IA corporativa, o que poderia aumentar Shadow AI.

## Decisão

AI Credits é indicador 0–100 não bloqueante: Usage 50, Training 30, License utilization 20, com saturação de uso e normalização quando licença for NOT_APPLICABLE.

## Alternativas consideradas

Crédito consumível; ranking punitivo; bloqueio por saldo; motor de regras configurável no MVP.

## Consequências

Métrica permanece simples e explicável. Alterações futuras na fórmula exigem decisão/versionamento explícito.

## Emenda (Sprint 4)

Os parâmetros do ADR-40, versão `credits-v1`, foram implementados no módulo
`engagement`:

- U = 100 × min(dias ativos no mês / 10, 1);
- T = 100 × min(conclusões de treinamento até o fim do mês, 1);
- L = 100 × licenças utilizadas / licenças aplicáveis; sem licença aplicável,
  `NOT_APPLICABLE` e a fórmula normaliza por 0,8: `(0,5U + 0,3T) / 0,8`;
- score = 0,5U + 0,3T + 0,2L, com 2 casas e HALF_UP;
- mês fechado usa snapshot imutável em `engagement_credit_snapshots`; mês
  corrente é `PROVISORIO` e não persiste;
- AI Credits continua não bloqueante: nenhum módulo de chat ou gateway depende
  de `engagement` para autorizar ou executar uma completion.
