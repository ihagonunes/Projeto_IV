# TASK-S3-B2 — Orçado × realizado e alerta (observability)

## Metadados
- **Onda:** B · **Depende de:** B1, A2 (`visibilityFor`)
- **Jira:** SPI-120, SPI-123, SPI-126
- **Módulo proprietário:** `observability` (consome `department.api`)
- **Spec:** `SPEC-02` §3, §4 (última linha), §6

## Critérios de aceitação
- [ ] `QueryBudgetStatus` com a regra de status da SPEC-02 §3, usando `BigDecimal` com 2 casas e
      HALF_UP.
- [ ] Custo real do mês somado em SQL nativo, com `tenant_id` explícito, janela de mês em UTC e
      intervalo `[início, fim)`.
- [ ] `GET /api/v1/observability/orcamentos?mes=AAAA-MM` (USAGE:READ, BT):
  - [ ] TenantWide → todos os deptos;
  - [ ] Departments → só os ids;
  - [ ] SelfOnly → 403.
- [ ] O alerta é calculado na leitura: sem tabela nova, sem evento novo, sem bloquear nada (A105).
- [ ] `ai/contracts.md` (linha `observability`):
  - [ ] marca `BudgetStatusQueryPort` como implementado;
  - [ ] registra que `AlertQueryPort` foi substituído pelo status calculado na leitura.
- [ ] Testes unitários de todos os limites da SPEC-02 §6.
- [ ] ITs:
  - [ ] MANAGER(A) vê só A;
  - [ ] COLLABORATOR recebe 403;
  - [ ] o custo de um mês não vaza para o seguinte;
  - [ ] depto sem orçamento aparece como `SEM_ORCAMENTO`.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
