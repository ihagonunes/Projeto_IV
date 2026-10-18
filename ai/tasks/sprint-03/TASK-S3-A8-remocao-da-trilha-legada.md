# TASK-S3-A8 — Remoção da trilha legada, `/me/permissoes` e regras ArchUnit

## Metadados
- **Onda:** A · **Ordem:** 8 · **Depende de:** A3, A7
- **Jira:** SPI-44 (manter e fechar com esta entrega)
- **Módulo proprietário:** `iam` (+ teste de arquitetura)
- **Spec:** `SPEC-01` §2.2 (itens removidos), §7.3, §10, §11 (AZ19, parte ArchUnit)

## Critérios de aceitação
- [ ] Remover o código da trilha legada:
  - [ ] `iam.api.model.Permission` e `AuthorizationScope`;
  - [ ] `AuthorizationQuery.checkPermission` e `resolveScope`, com as implementações;
  - [ ] em `User`: `permissions`, `isAdmin`, `hasPermission`, `grantPermission` e
        `revokePermission`;
  - [ ] o mapeamento `@ElementCollection` de `user_permission`;
  - [ ] `ProvisionUserCommand.permissions`;
  - [ ] `RegisterUser`, `RegisterUserCommand` e os testes deles.
- [ ] A tabela `user_permission` **permanece**: o `DROP` é no release seguinte (decisão 7).
- [ ] `/me/permissoes` e `/{id}/permissoes` retornam o formato da SPEC-01 §7.3 (concessões + união
      das permissões).
- [ ] Adicionar a `ArchitectureRulesTest` as regras 1 e 2 da SPEC-01 §10.
- [ ] `grep -rn "MANAGE_\|isAdmin()\|resolveScope\|checkPermission" src/main` não retorna nada.
- [ ] Suíte completa verde, com a contagem de testes no relatório.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
