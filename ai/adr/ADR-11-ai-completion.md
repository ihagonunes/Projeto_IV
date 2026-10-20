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

## Emenda (ADR-38)

- O `departmentId` deixou de ser informado pelo chamador: o Gateway resolve a lotação atual pelo
  `IdentityQuery` e aplica a ModelPolicy do departamento.
- Antes de chamar o provider, o Gateway grava a tentativa `IN_FLIGHT` em transação própria
  (`REQUIRES_NEW`). A tentativa é a fonte durável do que foi executado, mesmo se o processo falhar
  entre a chamada e a gravação do resultado.
- O `requestId` é sempre gerado pelo servidor; o chamador não o informa.
