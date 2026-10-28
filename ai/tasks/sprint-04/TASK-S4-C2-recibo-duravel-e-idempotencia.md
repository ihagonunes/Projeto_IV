# TASK-S4-C2 — Chat: recibo durável, idempotência do envio e testes de privacidade

## Metadados
- **Onda:** C · **Depende de:** C1, P1
- **Jira:**
  - **SPI-133** (redefinida: o recibo já vai no envio; falta sobreviver à recarga e bater com o
    `UsageRecord`);
  - **SPI-142** (testes de privacidade e isolamento).
- **Módulos:** `chat`; `aigateway.api` (`AttemptReceiptQuery`)
- **Spec:** `SPEC-07` §2 (`AttemptReceiptQuery`), §3 (mensagens com recibo, idempotência),
  §5 (CH01, CH02, CH05–CH07, CH10)

## Critérios de aceitação
- [ ] `AttemptReceiptQuery.receipts(tenant, owner, requestIds)`:
  - [ ] uma consulta em lote, com no máximo 100 ids;
  - [ ] filtro explícito por `tenant_id` **e** `user_id = owner`.
- [ ] `GET /conversas/{id}/mensagens`:
  - [ ] cada ASSISTANT traz `recibo` (ou `null` se a tentativa não existir mais);
  - [ ] paginação, padrão 50, máximo 200.
- [ ] `clientMessageId` nos dois POSTs, com as regras de idempotência da SPEC-07 §3; repetição não
      gera nova tentativa (CH07).
- [ ] Suíte de privacidade (SPI-142):
  - [ ] CH01 e CH02 cobrindo todas as rotas do chat, com MANAGER, EXECUTIVE, `TENANT_ADMIN` e
        outro tenant;
  - [ ] CH06;
  - [ ] CH10 (sem conteúdo em log).
- [ ] CH05: o recibo depois da recarga = o recibo do envio = o `UsageRecord`.
- [ ] `docs/development/CHAT_FRONTEND_BACKEND_PENDENCIAS.md`: marcar como resolvidos os itens
      §4.1, §4.2, §4.5 e §4.6 (parcial), com o contrato final.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
