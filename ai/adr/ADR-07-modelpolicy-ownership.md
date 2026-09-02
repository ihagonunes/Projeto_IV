# ADR-07 — ModelPolicy Ownership

## Status

ACCEPTED

## Contexto

Política de modelos é uma regra organizacional do Department, mas a execução pertence ao AI Gateway.

## Decisão

ModelPolicy pertence a department; ai-gateway valida IDs no catálogo, consulta e aplica allowedModelIds/defaultModelId. Administração orquestra validação sem criar ciclo Department→Gateway.

## Alternativas consideradas

ModelPolicy dentro do Gateway; duplicar políticas em ambos.

## Consequências

Mantém ownership organizacional e catálogo técnico separados, exigindo contrato explícito para validação dos ModelIds.
