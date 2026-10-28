# TASK-S4-H2 — Remover a tabela legada `user_permission` (V024)

> **ADIADA (S4-D8, 28/10/2026): fora da Sprint 4. Não executar.** Fica como pacote pronto para uma
> sprint futura; a V024 continua reservada para ela.

## Metadados
- **Onda:** H · **Ordem:** 2 · **Depende de:** H1 · **Bloqueio:** decisão **S4-D8** (operação
  destrutiva)
- **Jira:** **a criar**: "Remover tabela legada user_permission (V024)" (`INDEX.md` §6)
- **Módulo:** `iam`
- **Contexto:**
  - decisão 7 da Sprint 3 ("DROP no release seguinte");
  - ADR-36/37;
  - `UserJpaEntity` já não mapeia a tabela.

## Critérios de aceitação
- [ ] Confirmar com `grep -rn user_permission src/main` que só restam o comentário em
      `UserJpaEntity` e as migrations V004/V015.
- [ ] `V024__drop_legacy_user_permission.sql` com `DROP TABLE IF EXISTS user_permission;` e um
      comentário apontando ADR-36 e decisão 7.
- [ ] Atualizar o comentário do `UserJpaEntity`: a tabela foi removida na V024.
- [ ] O `V015MigrationIT` insere dados legados em `user_permission` antes da V015. Garantir que ele
      migre com `target("15")` explícito (e não até a última versão) e continue verde.
- [ ] IT de Flyway: migra até a V024; a tabela não existe; o `Hibernate validate` sobe; o login do
      bootstrap continua funcionando.
- [ ] O relatório lembra: **backup antes de aplicar em banco persistente** (`BANCO_E_IMPORTACAO_HISTORICA.md`).

## Checks
- `./scripts/check`
- `./scripts/check-integration`
