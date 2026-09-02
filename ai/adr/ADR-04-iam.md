# ADR-04 — IAM

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
