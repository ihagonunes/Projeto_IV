# ADR-09 — AI Catalog

## Status

ACCEPTED

## Contexto

Dataset e provedores externos misturam conceitos de ferramenta, fornecedor e modelo que precisam ser separados no domínio.

## Decisão

ai-gateway possui Provider, AITool e Model como conceitos distintos. Vários Providers podem estar ACTIVE simultaneamente; cada Model referencia um Provider.

## Alternativas consideradas

Provider=Model; AITool=Model; único provider ativo global.

## Consequências

Suporta múltiplos fornecedores e ferramentas sem acoplar políticas/licenças ao SDK específico.
