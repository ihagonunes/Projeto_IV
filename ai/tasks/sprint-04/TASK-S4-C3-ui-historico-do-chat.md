# TASK-S4-C3 — UI do chat: histórico, arquivar/excluir, seletor de modelos e recibos persistidos

## Metadados
- **Onda:** C · **Depende de:** C1, C2
- **Jira:** **SPI-141**
- **Responsável pela UI:** a autora do componente de chat (SPI-136, Lyn). **Outro agente não
  executa esta tarefa sem autorização explícita do responsável.**
- **Spec:** `SPEC-07` §3; `docs/development/CHAT_FRONTEND_BACKEND_PENDENCIAS.md`

## Objetivo
Trocar, no modo conectado (`/chat`), os comportamentos "sem API" por chamadas reais:
- listagem com `status`/`q`;
- renomear, arquivar, restaurar e excluir (com confirmação);
- seletor via `GET /conversas/modelos`;
- recibos vindos de `GET /mensagens` ao reabrir;
- `clientMessageId` em cada envio.

## Critérios de aceitação
- [ ] Nenhuma ação confirma sucesso sem a resposta da API. Falha → reconciliar com a lista do
      servidor.
- [ ] Conversa arquivada: compositor desabilitado e mensagem para o 409.
- [ ] O consumo pessoal informativo usa `GET /api/v1/observability/metrics/me/usage`. **Sem cota
      fictícia** no modo conectado (decisão S4-D11).
- [ ] A `/chat/previa` continua isolada das APIs.
- [ ] Testes `node --test src/test/frontend/chat.test.js` atualizados e verdes.
- [ ] Nenhum conteúdo via `innerHTML`.

## Checks
- `node --test src/test/frontend/*.test.js`
- Verificação manual com o STUB
