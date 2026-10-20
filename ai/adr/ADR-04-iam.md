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

## Emenda (ADR-36/ADR-37) — 2026-10-18

Esta seção atualiza a decisão; o texto original acima permanece como registro histórico
e o Status não muda.

- O "admin" deixa de ser um conjunto de permissões e passa a ser o **papel de sistema
  `TENANT_ADMIN`**, com escopo `TENANT` e posto fora da escada (ADR-36).
- O **escopo** (`SELF`/`DEPARTMENT`/`TENANT`) passa a ser atributo explícito da
  **concessão** (`iam_role_assignments.scope_type`), não do papel nem de um
  `AuthorizationScope` resolvido em runtime (ADR-37).
- O contrato público passa a ser `AuthorizationQuery.isAllowed`/`visibilityFor`, com
  `AccessTarget`/`VisibilityScope` no `sharedkernel.rbac`; `AuthorizationScope` e
  `checkPermission`/`resolveScope` são removidos (TASK-S3-A8).
- `TENANT_ADMIN` **não** possui `CHAT`: o teto de CHAT é sempre `SELF` (ADR-36, item 5).
