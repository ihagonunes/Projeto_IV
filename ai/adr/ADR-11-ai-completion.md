# ADR-11 — AI Completion

## Status

ACCEPTED

## Contexto

Chat e Agent precisam de resposta do modelo para concluir o mesmo caso de uso.

## Decisão

AICompletionService do ai-gateway é síncrono. requestedModelId é opcional e, quando presente, deve estar em ModelPolicy.allowedModelIds; caso contrário usa default. Depois valida Model AVAILABLE e Provider ACTIVE.

## Alternativas consideradas

Fila assíncrona obrigatória; endpoint público genérico; routing por custo/latência.

## Consequências

Fluxo do usuário permanece simples e determinístico. Provider concreto fica encapsulado em adapter.
