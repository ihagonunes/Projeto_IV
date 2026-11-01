# ADR-39 — Chat privado e recibo de execução

## Status

ACCEPTED (2026-10-18).

## Contexto

- O módulo `chat` existe só como esqueleto.
- A Sprint 3 precisa de um chat demonstrável (SPI-136, UI com a Lyn) e de um recibo de execução
  (SPI-134).
- ADR-16 exige privacidade total do conteúdo: nem MANAGER nem ADMIN leem a conversa.

## Decisão

1. **Módulo `chat`.** Dono de `Conversation` (tenant, dono, título, status, timestamps) e `Message`
   (conversa, papel `USER`/`ASSISTANT`, conteúdo, `request_id` da completion). Tabelas
   `chat_conversations` e `chat_messages` (V018), com `tenant_id` explícito e FK só dentro do
   módulo.
2. **Dependências.** Pela allowlist: `sharedkernel`, `iam.api`, `aigateway.api`. A mensagem do
   usuário é enviada por `AICompletionService` (ADR-38), com o departamento atual do usuário
   (política de modelos, ADR-07).
3. **Autorização.**
   - Gate: `CHAT:CREATE` (enviar) e `CHAT:READ` (ler), com `targeting = BY_TARGET`.
   - O caso de uso exige `owner == ator` (`OwnedTarget`, teto SELF do ADR-36).
   - Conversa de outro usuário ou de outro tenant → **404**, para não revelar a existência.
   - `TENANT_ADMIN` não tem `CHAT`.
4. **Recibo.** A resposta do envio inclui só metadados, ligados à tentativa e ao `UsageRecord` pelo
   `request_id`: `requestId`, modelo, provider, tokens, custo, `costStatus` e latência.
5. **Analytics.** Nenhum conteúdo de mensagem entra em evento, log ou analytics. Só metadados vão ao
   `observability`.
6. **Endpoints.**
   - `POST /api/v1/conversas`
   - `GET /api/v1/conversas` (paginado; só as próprias)
   - `GET /api/v1/conversas/{id}/mensagens`
   - `POST /api/v1/conversas/{id}/mensagens` (devolve a resposta e o recibo)

## Alternativas consideradas

- Conversa sem persistência: inviabiliza histórico e recibo.
- Admin com leitura para suporte: viola o ADR-16.
- Chat chamando o provider direto: viola o ADR-11 e a origem única de uso (ADR-13).

## Consequências

- Quatro endpoints novos (acima do teto do plano; precisa de aprovação, ver
  `ai/tasks/sprint-03/INDEX.md`).
- Exclusão e arquivamento ficam fora do MVP da Sprint 3.
- A UI (SPI-136) consome exatamente esses contratos.

## Emenda (Sprint 4)

O ciclo de vida e o recibo durável foram detalhados no ADR-42:

- a conversa passa a ter `ACTIVE`, `ARCHIVED` e `DELETED` (exclusão lógica com
  `deleted_at`); `DELETED` nunca aparece nas listagens;
- `PATCH /api/v1/conversas/{id}` altera título e status; `DELETE` faz a exclusão
  lógica; `GET /api/v1/conversas/modelos` expõe a política do departamento;
- `client_message_id` (8–64, `[A-Za-z0-9_-]`) torna o turno idempotente; a
  repetição concluída devolve o mesmo turno e não duplica mensagem, tentativa ou
  recibo;
- o recibo continua sendo metadados ligados à tentativa e ao `UsageRecord`, sem
  conteúdo de mensagem em log, evento ou analytics.
