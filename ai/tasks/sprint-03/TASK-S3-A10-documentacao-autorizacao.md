# TASK-S3-A10 — Documentação canônica da autorização

## Metadados
- **Onda:** A · **Ordem:** 10 (fecha a onda) · **Depende de:** A1–A9
- **Jira:** SPI-51 (documentação da matriz)

## Critérios de aceitação
- [ ] Registrar emendas, numa seção "Emenda (ADR-36/37)", nos ADRs:
  - [ ] ADR-04: admin passa a ser o papel `TENANT_ADMIN`;
  - [ ] ADR-33: escopo explícito na concessão; corrigir o texto "não persistido na tupla";
  - [ ] ADR-34: `targeting`.
- [ ] **Não** alterar o Status de nenhum ADR. Os ADRs 36/37 já estão ACCEPTED (18/10/2026); as emendas entram como seção nova.
- [ ] Corrigir drift anterior (auditoria `docs/review/AUDITORIA_DOCUMENTACAO.md`): ADR-30,
      `ai/architecture.md` (Segurança) e `ai/testing.md` passam a citar o ADR-32 (`tenantSlug`
      opcional no login; tenant resolvido pelo domínio do e-mail).
- [ ] `ai/contracts.md`: novo `AuthorizationQuery`, `Targeting` e tipos de `sharedkernel.rbac`.
- [ ] `ai/architecture.md`:
  - [ ] ownership do `sharedkernel` e do `iam` atualizados;
  - [ ] a seção Segurança registra que as concessões são lidas a cada requisição.
- [ ] `ai/decisions.md`: marcar a "Simplificação 2" (`POST /usuarios` sem senha) como superada.
- [ ] Criar `docs/development/MATRIZ_PERMISSOES.md` (SPI-51), conferido contra o código final, com:
  - [ ] a tabela §1 da SPEC-01;
  - [ ] as regras de alvo e de visibilidade;
  - [ ] a tabela de endpoints.
- [ ] Atualizar `docs/development/CONTRATO_API_FRONT_BACK.md` com os payloads da SPEC-01 §7.

## Checks
- `./scripts/check`
