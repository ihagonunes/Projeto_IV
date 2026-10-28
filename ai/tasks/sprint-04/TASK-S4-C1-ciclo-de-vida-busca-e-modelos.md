# TASK-S4-C1 — Chat: arquivar, excluir, renomear, busca e modelos do usuário

## Metadados
- **Onda:** C · **Depende de:** H1; ADR-42 ACCEPTED
- **Jira:**
  - **SPI-139** (redefinida: V022 evolui o schema existente; tokens e custo **não** vão para
    `chat_messages`);
  - **SPI-140** (redefinida: rotas `/api/v1/conversas`, não `/api/v1/chat/conversations`).
- **Módulos:** `chat`; `aigateway.api` (só a listagem `allowedModels`)
- **Spec:** `SPEC-07-chat-evolucao.md` §1, §2 (`allowedModels`), §3 (lista, modelos, PATCH,
  DELETE, arquivada → 409), §5 (CH01–CH04, CH08, CH09)

## Critérios de aceitação
- [ ] `V022__chat_lifecycle_and_idempotency.sql` como na SPEC-07 §1. Ela já cria a coluna
      `client_message_id`, usada na C2.
- [ ] Domínio:
  - [ ] `ConversationStatus.DELETED`;
  - [ ] `Conversation.rename/archive/restore/delete` com as invariantes (excluída não volta;
        arquivada não recebe envio).
- [ ] `GET /conversas` com `status` e `q` (escape do ILIKE) e ordem estável.
- [ ] `PATCH /conversas/{id}` e `DELETE /conversas/{id}` (204).
- [ ] `GET /conversas/modelos` usa `ModelResolutionQuery.allowedModels` (novo).
- [ ] Erros com `code` estável (SPEC-07 §3).
- [ ] Testes CH01–CH04, CH08 e CH09.
- [ ] `ai/contracts.md` (seção chat e `aigateway.api`) atualizado.

## Restrição
Não alterar o front do chat (S4-C3 é da responsável pelo componente).

## Checks
- `./scripts/check`
- `./scripts/check-integration`
