# ADR-17 — Engagement

## Status

ACCEPTED

## Contexto

Licenças e treinamentos são fatos transacionais; adoção e créditos são métricas derivadas.

## Decisão

Engagement é dono de AILicense/TrainingRecord e deriva métricas. EligibleUsers vem apenas de IAM Query Port; existe no máximo uma licença ACTIVE por tenant+user+AITool. Histórico usa HistoricalEngagementAdapter.

## Alternativas consideradas

Projeção local de usuários; múltiplas licenças ativas iguais; transformar histórico em entidades operacionais.

## Consequências

Evita drift de identidade e mantém histórico separado, ao custo de depender de queries de outros módulos para cálculo.

## Emenda (Sprint 4)

- Fontes e elegíveis são os do ADR-40: o módulo `engagement` usa o operacional e o
  histórico **separadamente**, nunca somando as duas fontes; a população elegível
  vem de `iam.api.IdentityQuery.eligibleUsers` (usuários ACTIVE, excluindo quem só
  possui `TENANT_ADMIN`).
- A V023 cria `engagement_ai_licenses`, `engagement_training_records` e
  `engagement_credit_snapshots`. Licenças e treinos são transacionais; os
  snapshots mensais de AI Credits são imutáveis.
- O histórico é lido por adapter nativo próprio, com `company_id` explícito;
  a cobertura (`eligibleTotal`, `eligibleLinked`, status não mapeados) acompanha
  as respostas.
- Os endpoints `/api/v1/engajamento/*` aplicam `USAGE:READ` e a visibilidade IAM
  sobre os elegíveis; a lista nominal de treinamento exige `USER:READ` sobre cada
  usuário.
