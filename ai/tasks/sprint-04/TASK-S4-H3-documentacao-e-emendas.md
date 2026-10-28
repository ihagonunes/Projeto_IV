# TASK-S4-H3 — Documentação canônica e emendas da Sprint 4

## Metadados
- **Onda:** H · **Ordem:** última · **Depende de:** todas as tarefas entregues
- **Jira:** **SPI-137** (redefinida: o ADR-35 já está ACCEPTED na opção A — **sem** pgvector e
  **sem** Spring AI; a tarefa passa a registrar as emendas e as dependências desta sprint)

## Critérios de aceitação
- [ ] Emendas, numa seção "Emenda (Sprint 4)", **sem** alterar o Status:
  - [ ] ADR-17: fontes e elegíveis (aponta para o ADR-40);
  - [ ] ADR-19: parâmetros `credits-v1` (aponta para o ADR-40);
  - [ ] ADR-39: ciclo de vida, recibo durável e idempotência (aponta para o ADR-42);
  - [ ] ADR-06: projeção (aponta para o ADR-41);
  - [ ] ADR-38: preço obrigatório, se a P1 não tiver feito.
- [ ] `ai/contracts.md` conferido contra o código final (`iam.api`, `observability.api`,
      `aigateway.api`, `chat`, `engagement`).
- [ ] `ai/architecture.md`: ownership do `engagement` (licenças, treinos, snapshots de Credits).
- [ ] `ai/dependencies.md`:
  - [ ] confirmar "nenhuma dependência nova na Sprint 4";
  - [ ] seção do front (npm) se ainda faltar (auditoria, item 12).
- [ ] Docs de desenvolvimento atualizados:
  - [ ] `docs/development/CONTRATO_API_FRONT_BACK.md`: as rotas novas;
  - [ ] `DASHBOARD_INDICADORES_E_CALCULOS.md`: marcar adoção, treinamento e Credits como
        implementados, com a versão;
  - [ ] `VALIDACAO_E_PENDENCIAS.md`: evidências da sprint.
- [ ] O responsável marca os ADRs 40–42 como ACCEPTED; o implementador não.

## Checks
- `./scripts/check`
