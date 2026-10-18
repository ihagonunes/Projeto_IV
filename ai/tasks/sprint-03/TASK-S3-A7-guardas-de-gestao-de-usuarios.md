# TASK-S3-A7 — Guardas na gestão de usuários e remoção de `POST /usuarios`

## Metadados
- **Onda:** A · **Ordem:** 7 · **Depende de:** A5, A6
- **Jira:** **criar** "Guardas de elevação na gestão de usuários (N03)"; registrar reabertura parcial de SPI-63
- **Módulo proprietário:** `iam`
- **Spec:** `SPEC-01` §4 (`domina`, guardas), §6 (linhas de usuários), §11 (AZ05, AZ06, AZ08, AZ09, AZ14, AZ15, AZ16)

## Critérios de aceitação
- [ ] Removidos `POST /api/v1/usuarios`, `CreateUser` e os testes deles (decisão 3), sem callers
      remanescentes.
- [ ] `PATCH /usuarios/{id}`, `PATCH /usuarios/{id}/status` e `POST /usuarios/{id}/nivel` passam a
      `BY_TARGET`, com `domina(ator, alvo)`:
  - [ ] ator = alvo → 403;
  - [ ] novo depto fora do escopo do ator → 403;
  - [ ] desativar o último TENANT_ADMIN → 409;
  - [ ] o rótulo continua sem efeito de autorização.
- [ ] `GET /usuarios` filtrado por `visibilityFor(USER:READ)` (`UserSearchFilter` ganha esse
      recorte).
- [ ] `GET /usuarios/{id}/permissoes` checa `isAllowed(OwnedTarget)`.
- [ ] Usuário de outro tenant → 404 (convenção da SPEC-01 §8).
- [ ] Reescrever `UserManagementIT.shouldUpdateUserHierarchyLevelAndDepartment` (falso-positivo T1
      do review) para provar o efeito correto.
- [ ] ITs AZ05, AZ06, AZ08, AZ09 (HTTP), AZ14 e AZ16.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
