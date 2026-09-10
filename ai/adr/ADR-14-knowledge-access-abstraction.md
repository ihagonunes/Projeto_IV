# ADR-14 — Knowledge Access Abstraction

## Status

ACCEPTED

## Contexto

RAG pode ser útil, mas não é requisito obrigatório do MVP nem deve contaminar o domínio.

## Decisão

Agent Policies depende apenas de KnowledgeAccessService. Implementação pode ser busca simples, RAG ou outra estratégia, sempre tenant/scope-aware.

## Alternativas consideradas

pgvector obrigatório; SDK de RAG dentro do domínio.

## Consequências

Permite começar simples e evoluir retrieval sem alterar casos de uso ou autorização.
