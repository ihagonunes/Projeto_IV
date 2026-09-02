# ADR-03 — Multi-tenancy

## Status

ACCEPTED

## Contexto

A plataforma white-label atende múltiplas empresas em uma mesma aplicação e banco.

## Decisão

Usar tenant_id + TenantContext transversalmente. TenantId vem do contexto autenticado; queries nativas e históricos tenant-aware filtram explicitamente. Testes com pelo menos dois tenants são obrigatórios.

## Alternativas consideradas

Banco por tenant; schema por tenant; confiar tenant_id enviado pelo frontend.

## Consequências

Reduz custo operacional, porém qualquer nova persistência/query precisa comprovar isolamento e evitar vazamento entre tenants.
