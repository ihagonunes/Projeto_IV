# TASK-S4-E5 — AI Credits (0–100) com snapshot mensal e prova de não bloqueio

## Metadados
- **Onda:** E · **Ordem:** 5 · **Depende de:** E2 (E3/E4 recomendados)
- **Jira:**
  - **SPI-147** (redefinida: snapshot na **V023**, `engagement_credit_snapshots`, não V020);
  - **SPI-148**.
- **Módulo:** `engagement` (+ regra ArchUnit e IT no `chat`/`aigateway`)
- **Spec:** `SPEC-06` §3 (`CreditsCalculator`), §5.3, §7; parâmetros da decisão **S4-D4**

## Critérios de aceitação
- [ ] `GET /api/v1/engajamento/creditos?fonte=&mes=&departmentId=`:
  - [ ] USAGE:READ, BY_TARGET;
  - [ ] mês fechado lê o snapshot, ou calcula e persiste com `INSERT … ON CONFLICT DO NOTHING`;
  - [ ] mês corrente → `PROVISORIO`, sem persistir;
  - [ ] mês futuro → 400.
- [ ] Parâmetros `credits-v1` como constantes de domínio. Sem configuração em runtime (ADR-19).
- [ ] Testes:
  - [ ] EN05 (completo);
  - [ ] EN06 (snapshot imutável);
  - [ ] EN07 (créditos: COLLABORATOR só a própria linha).
- [ ] SPI-148:
  - [ ] regra ArchUnit "nenhuma classe de aigateway/chat/agentpolicies/iam depende de engagement";
  - [ ] IT EN12: usuário com Credits 0 conversa normalmente (STUB);
  - [ ] apagar os dados de `engagement_*` não muda o chat.
- [ ] `ai/contracts.md` documenta a rota.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
