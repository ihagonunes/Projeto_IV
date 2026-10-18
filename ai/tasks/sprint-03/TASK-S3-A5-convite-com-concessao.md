# TASK-S3-A5 — Convite carrega a concessão (dominância, hash, revalidação no aceite)

## Metadados
- **Onda:** A · **Ordem:** 5 · **Depende de:** A2, A4
- **Jira:** SPI-46 (redefinir)
- **Módulo proprietário:** `iam`
- **Spec:** `SPEC-01` §4 (`podeConceder`), §6 (linhas de convites), §7.2, §8, §11 (AZ02, AZ03, AZ07, AZ13, AZ17)

## Objetivo
Fechar o N01 para usuários convidados:
- o aceite cria usuário e concessão na mesma transação;
- vale a regra de delegação aprovada (decisão 5);
- valem as regras de convite aprovadas (decisão 6).

## Critérios de aceitação
- [ ] `CreateInviteRequest`:
  - `roleKey` obrigatório;
  - `departmentId` obrigatório quando o papel é DEPARTMENT;
  - `hierarchyLevel` opcional.
- [ ] O convite grava `invited_by` = ator e o `role_id` do papel no tenant.
- [ ] `podeConceder` fica num serviço de aplicação do `iam`, reutilizado por A6 e A7:
  - [ ] MANAGER(A) só concede COLLABORATOR ou LEADER no depto A;
  - [ ] só TENANT_ADMIN concede MANAGER, EXECUTIVE e TENANT_ADMIN.
- [ ] Token do convite:
  - [ ] gerado com `SecureRandom` (≥ 128 bits);
  - [ ] devolvido uma única vez;
  - [ ] persistido só como SHA-256 em hex, com busca pelo hash;
  - [ ] nunca logado.
- [ ] `AcceptInvite`:
  - [ ] revalida `podeConceder(invitedBy, papel, depto)` com as concessões **atuais** do convidante;
  - [ ] se falhar → 409 e nada é criado;
  - [ ] se passar → cria o usuário (lotação = `departmentId` do convite) e a concessão
        (origem INVITE, `granted_by` = convidante).
- [ ] Listagem, reenvio e revogação de convites ficam restritos aos convites que o ator pode
      conceder.
- [ ] `OnboardingFlowIT` (`HANDOFF.md` §5) verde.
- [ ] ITs AZ02, AZ07, AZ13 e AZ17.
  - AZ13 verifica também que o banco não guarda o token puro e que o log capturado no teste não o
    contém.

## Contexto para o agente
- ADR-37
- `SPEC-01`
- Código: `CreateInvite`, `AcceptInvite`, `ResendInvite`, `RevokeInvite`, `ListInvites`, `Invite`,
  `InviteController`, DTOs e `InviteJpa*`

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
