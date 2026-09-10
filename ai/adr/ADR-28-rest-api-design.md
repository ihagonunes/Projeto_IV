# ADR-28 — REST API Design

## Status

ACCEPTED

## Contexto

A API precisa de convenções estáveis sem expor a arquitetura interna diretamente.

## Decisão

Usar /api/v1/{resource}, controllers por módulo e REST→application use case. Tenant vem do contexto autenticado. Login em /api/v1/auth/login. Paginação page/size zero-based, default 20, máximo 100. Não existe /ai/complete público.

## Alternativas consideradas

Endpoints por nome de módulo; expor ports internos diretamente; tenant_id como parâmetro confiado.

## Consequências

Contrato HTTP fica previsível e desacoplado dos detalhes internos do Monólito Modular.
