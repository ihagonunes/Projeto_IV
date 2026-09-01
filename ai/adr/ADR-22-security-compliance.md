# ADR-22 — Security Compliance

## Status

ACCEPTED

## Contexto

Eventos de segurança precisam ser normalizados pelo volume de uso para composição do Governance Score.

## Decisão

SecurityComplianceRate = (1 - violatingSecurityEvents/totalAIRequests)×100, limitado a [0,100]. Sem atividade suficiente, retorna INSUFFICIENT_DATA.

## Alternativas consideradas

Contagem bruta de eventos; permitir valores negativos; score arbitrário por severidade no MVP.

## Consequências

Métrica fica comparável entre escopos de tamanhos diferentes e evita resultados fora do domínio esperado.
