# ADR-04 — IAM

> ⚠️ **Emendado pelos ADR-36 e ADR-37 (ACCEPTED, 2026-10-18).** "ADMIN = conjunto de permissões" e `AuthorizationScope` deixam de valer. O admin passa a ser o papel de sistema `TENANT_ADMIN`; o escopo pertence à concessão. A emenda formal entra pela TASK-S3-A10.

## Status

ACCEPTED

## Contexto

Identidade, hierarquia e autorização não podem ser reimplementadas em cada módulo.

## Decisão

IAM é dono de Tenant/User, autenticação, HierarchyLevel, Permissions e AuthorizationScope. ADMIN é conjunto de permissões, não nível hierárquico. Outros módulos consultam IdentityQuery/AuthorizationQuery.

## Alternativas consideradas

RBAC espalhado por controllers; ADMIN como cargo; autorização delegada ao frontend.

## Consequências

Centraliza regras de segurança e facilita revogação. Módulos de negócio dependem de contratos IAM em vez de conhecer sua persistência.
