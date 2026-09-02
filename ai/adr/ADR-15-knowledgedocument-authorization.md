# ADR-15 — KnowledgeDocument Authorization

## Status

ACCEPTED

## Contexto

Documentos corporativos podem ter escopo por Department/Area e exigem administração controlada.

## Decisão

Create/version/submit/approve/archive são ADMIN-only. Leitura exige mesmo tenant, versão ACTIVE e correspondência OR com ao menos um Department/Area scope; autorização ocorre antes do retrieval/LLM.

## Alternativas consideradas

LLM decide acesso; managers podem ler tudo; AND entre múltiplos scopes.

## Consequências

Impede vazamento de conhecimento e preserva política determinística independente do modelo.
