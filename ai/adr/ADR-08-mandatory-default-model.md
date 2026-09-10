# ADR-08 — Mandatory Default Model

## Status

ACCEPTED

## Contexto

O routing precisa ser determinístico quando o usuário não solicita modelo.

## Decisão

Se allowedModelIds não estiver vazio, defaultModelId é obrigatório e deve pertencer ao conjunto permitido. Conjunto vazio implica default nulo.

## Alternativas consideradas

Escolha aleatória; primeiro modelo da lista; ausência de default.

## Consequências

Elimina ambiguidades de execução e torna configuração inválida detectável antes da completion.
