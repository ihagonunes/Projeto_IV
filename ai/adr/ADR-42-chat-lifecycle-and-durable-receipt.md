# ADR-42 — Ciclo de vida da conversa, recibo durável e modelos do usuário

## Status

ACCEPTED (2026-10-28), com as recomendações das decisões S4-D2 a S4-D13 (`ai/tasks/sprint-04/INDEX.md` §2).

## Contexto

O ADR-39 entregou o chat privado com 4 endpoints e deixou arquivamento e exclusão fora do MVP
("exigem revisão formal"). A Sprint 4 (SPI-139 a 142) e as pendências levantadas pela UI
(`docs/development/CHAT_FRONTEND_BACKEND_PENDENCIAS.md`) pedem:

- renomear, arquivar e excluir conversas;
- recibo que sobreviva à recarga da página;
- seletor com os modelos que o usuário realmente pode usar;
- busca no histórico.

Hoje:
- `chat_conversations.status` aceita só `ACTIVE`/`ARCHIVED`;
- o recibo só existe na resposta do envio;
- `MessageResponse` traz o `requestId`, mas não os metadados.

## Decisão

1. **Ciclo de vida.** Estados `ACTIVE`, `ARCHIVED` e `DELETED`:
   - arquivar e restaurar são reversíveis; conversa arquivada não aceita envio (409);
   - excluir é lógico: `status = DELETED` e `deleted_at`;
   - conversa excluída some de toda leitura (404) e não pode ser restaurada pela API;
   - o conteúdo fica retido no banco, sem expurgo automático nesta sprint (decisão S4-D7);
   - tudo continua exclusivo do dono: outro usuário, admin ou outro tenant → 404 (ADR-16/39).
2. **Recibo durável sem duplicar dados.** O chat **não** copia tokens e custo para
   `chat_messages`:
   - `GET /conversas/{id}/mensagens` enriquece cada mensagem ASSISTANT com `recibo`;
   - os dados vêm de uma consulta em lote no `aigateway.api` (`AttemptReceiptQuery.receipts(tenant,
     owner, requestIds)`), que só devolve tentativas cujo `user_id` é o dono;
   - fonte única: a tentativa (ADR-38). Os valores batem com o `UsageRecord` pelo `requestId`
     (critério da SPI-133).
3. **Modelos do usuário.** `GET /api/v1/conversas/modelos` lista os modelos da política do
   departamento atual do usuário:
   - inclui padrão, indisponibilidade e nome do provider;
   - usa o `ModelResolutionQuery` do `aigateway.api`, sem ampliar `AI_TOOL:MANAGE`;
   - o envio continua revalidando a política.
4. **Busca.** `GET /conversas?status=&q=` filtra título por `ILIKE` escapado, sempre com
   `tenant_id` e `owner_user_id`.
   - Ordenação estável: `updated_at DESC, id DESC`.
   - Busca no conteúdo das mensagens fica fora (custo e privacidade).
5. **Rotas.** Ficam as existentes, em português (`/api/v1/conversas`). As rotas em inglês sugeridas
   na SPI-140 (`/api/v1/chat/conversations`) **não** são criadas.

## Alternativas consideradas

- **Tokens e custo em `chat_messages`** (SPI-139 literal): duplica a fonte da tentativa e diverge
  quando a entrega é reprocessada.
- **Exclusão física:** perde o vínculo com `UsageRecord`/tentativa para auditoria e impede
  reconciliação.
- **Varrer `/ai-gateway/tentativas` no front:** exige `USAGE:READ` em escopo e não garante o dono.

## Consequências

- **Migration V022:** `status` aceita `DELETED`; nova coluna `deleted_at`; índice de busca.
- **Endpoints:** `PATCH /conversas/{id}`, `DELETE /conversas/{id}` e `GET /conversas/modelos`.
- **Contratos novos em `aigateway.api`:** `AttemptReceiptQuery` e a listagem em
  `ModelResolutionQuery`.
- A retenção e o expurgo do conteúdo excluído continuam como decisão futura (LGPD).
