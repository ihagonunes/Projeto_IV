# ADR-05 — User Area Ownership

## Status

ACCEPTED

## Contexto

User pertence a um Department, mas pode atuar em múltiplas Areas do mesmo Department.

## Decisão

IAM armazena DepartmentId no User; department é dono de UserAreaMembership. Transferir Department encerra todas as memberships ativas antigas antes da mudança; novas Areas são atribuídas explicitamente.

## Alternativas consideradas

Area dentro de IAM; associação User-Area many-to-many cross-module; herança automática de Areas.

## Consequências

Evita duplicação de estrutura organizacional e deixa o ciclo de membership auditável.
