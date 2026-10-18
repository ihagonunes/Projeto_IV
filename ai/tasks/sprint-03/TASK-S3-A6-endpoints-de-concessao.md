# TASK-S3-A6 — Papéis e concessões: listar, atribuir (admin) e revogar

## Metadados
- **Onda:** A · **Ordem:** 6 · **Depende de:** A5 (reusa `podeConceder`)
- **Jira:** SPI-47 (redefinir); SPI-48 (parte backend)
- **Módulo proprietário:** `iam`
- **Spec:** `SPEC-01` §6 (3 endpoints novos), §7.4, §7.5, §8, §11 (AZ04, AZ11, AZ15)

## Critérios de aceitação
- [ ] `GET /api/v1/papeis` (ROLE:READ, BT), com `concedivel` calculado para o ator.
- [ ] `POST /api/v1/usuarios/{id}/concessoes` (ROLE:MANAGE, TO):
  - [ ] só TENANT_ADMIN;
  - [ ] ator = alvo → 403;
  - [ ] concessão ativa idêntica → 409;
  - [ ] origem DIRECT.
- [ ] `DELETE /api/v1/usuarios/{id}/concessoes/{concessaoId}` (ROLE:MANAGE, TO):
  - [ ] revogação lógica;
  - [ ] ator = alvo → 403;
  - [ ] último TENANT_ADMIN ativo do tenant → 409;
  - [ ] concessão de outro tenant ou de outro usuário → 404.
- [ ] Casos de uso `AssignRole` e `RevokeRoleAssignment` em `iam.application`.
- [ ] ITs:
  - [ ] AZ04;
  - [ ] AZ11 (mesmo JWT após a revogação → 403);
  - [ ] AZ15.

## Contexto para o agente
- ADR-37 (itens 5, 6 e 6a)
- `SPEC-01`

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
