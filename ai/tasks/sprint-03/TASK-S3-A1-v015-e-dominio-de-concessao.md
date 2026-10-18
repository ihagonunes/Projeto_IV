# TASK-S3-A1 — V015 e domínio de concessão com escopo

## Metadados
- **Onda:** A (autorização) · **Ordem:** 1 · **Depende de:** ADR-36/37 ACCEPTED
- **Jira:** SPI-43 (redefinir), SPI-42 (redefinir)
- **Módulo proprietário:** `iam` (+ tipos em `sharedkernel.rbac`)
- **Spec:** `SPEC-01-autorizacao.md` §1, §2.1, §2.3, §5

## Objetivo
Persistir concessões com escopo explícito e trocar a matriz por nível pelos papéis de sistema, sem
mudar ainda o comportamento dos gates HTTP.

## Fora de escopo
- Avaliador e anotação (A2).
- Bootstrap do admin (A4).
- Regras do convite (A5).
- Remoção da trilha legada (A8).

Seeder demo e ITs continuam funcionando com a menor adaptação possível: trocar
`RoleAssignment.create(...)` por `RoleAssignment.grant(..., GrantOrigin.MIGRATION, null, now)`.

## Critérios de aceitação
- [ ] `sharedkernel.rbac.{ScopeType, AccessTarget, VisibilityScope}` criados conforme SPEC-01 §2.1.
- [ ] `SystemRole` e `GrantOrigin` criados.
- [ ] `PermissionMatrix` e `PermissionMatrixTest` removidos.
- [ ] `ProvisionDefaultRoles` provisiona os 5 papéis a partir de `SystemRole`, incluindo
      `TENANT_ADMIN`.
- [ ] `RoleAssignment` tem os novos campos, as invariantes, `grant`, `revoke` e `covers`
      (SPEC-01 §2.3).
- [ ] Entidade JPA e mapper atualizados.
- [ ] A porta ganha `findActiveByTenantIdAndUserId`; a revogação é um update via `save`.
- [ ] `V015__iam_scoped_grants.sql` exatamente como SPEC-01 §5. Ajustes só com justificativa no
      relatório.
- [ ] `V015MigrationIT` (Flyway programático: `target("14")` → dados legados → `migrate()`) cobre:
  - [ ] AZ18a, AZ18b, AZ18c;
  - [ ] a parte de banco de AZ09: a FK composta rejeita papel de outro tenant.
- [ ] Testes unitários de `SystemRole`:
  - [ ] contagens 5/7/13/14/20;
  - [ ] inclusão COLLABORATOR ⊆ LEADER ⊆ MANAGER ⊆ EXECUTIVE;
  - [ ] TENANT_ADMIN sem CHAT.
- [ ] Testes unitários de `RoleAssignment.covers`: escopos SELF, DEPARTMENT e TENANT × os 3 tipos
      de alvo.
- [ ] Suítes unit e integração verdes.

## Ponto de atenção (convites)
A V015 renomeia `iam_invites.token` → `token_hash` e exige `role_id` em convite `PENDING`. Para a
aplicação subir, **neste mesmo PR**:
- ajuste o mapeamento JPA do convite;
- `CreateInvite` grava `sha256(token)` em hex e devolve o token puro uma vez;
- `AcceptInvite` busca pelo hash;
- provisoriamente, `CreateInvite` grava `role_id` do papel cujo nome é igual ao `hierarchyLevel`
  pedido.

A regra definitiva de papel no convite é a A5.

## Contexto para o agente
- `AGENTS.md`
- `ai/architecture.md` (Persistência)
- ADR-36, ADR-37
- `SPEC-01` §1–§5
- `docs/review/VERIFICACAO_FASE0.md` (V13, D7, D15)

## Arquivos prováveis
- `src/main/java/br/com/qsoft/governance/sharedkernel/rbac/*`
- `src/main/java/br/com/qsoft/governance/iam/domain/rbac/{SystemRole,GrantOrigin,Role,RoleAssignment}.java`
- `src/main/java/br/com/qsoft/governance/iam/application/rbac/ProvisionDefaultRoles.java`
- `src/main/java/br/com/qsoft/governance/iam/application/port/RoleAssignmentRepository.java`
- `src/main/java/br/com/qsoft/governance/iam/infrastructure/persistence/{RoleAssignment*,Invite*}`
- `src/main/java/br/com/qsoft/governance/iam/application/{CreateInvite,AcceptInvite}.java` (só o hash)
- `src/main/resources/db/migration/V015__iam_scoped_grants.sql`
- `src/test/java/br/com/qsoft/governance/iam/infrastructure/V015MigrationIT.java` (novo)

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
