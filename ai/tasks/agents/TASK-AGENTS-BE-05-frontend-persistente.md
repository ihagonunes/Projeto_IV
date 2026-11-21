# TASK-AGENTS-BE-05 — Frontend persistente da praça

## Metadados

- **Jira:** SPI-180.
- **Depende de:** BE-02, BE-03, BE-04.
- **Módulo:** frontend (`static/public/js/pages`, `static/modules/chat`).
- **Autoria:** implementação pelo agente, com revisão da responsável pelo chat.
- **Não depende de:** ADR-45.

## Objetivo

Ligar a praça existente ao catálogo e à persistência reais, mantendo a prévia
isolada e sem alterar o turno do chat.

## Fora de escopo

- execução de agentes;
- mudança no envio de mensagens do chat;
- novos providers/modelos;
- mobile.

## Critérios de aceitação

- [ ] `agent-plaza.js` recebe catálogo real via API; não usa o catálogo hardcoded
  na produção.
- [ ] `merge` continua sendo nó sintetizado pela UI, não `AgentDefinition`.
- [ ] Grafo salvo inclui `schemaVersion`; IDs de nós são opacos/UUID e não
  `step-N` do cliente.
- [ ] `mount()`/serviço do frontend:
  - [ ] carrega fluxo existente;
  - [ ] salva com `row_version`/`If-Match`;
  - [ ] trata 409/412 com recarregar/sobrescrever;
  - [ ] `onConfirm` chama save/publicação real;
  - [ ] não confirma sucesso sem resposta do servidor.
- [ ] `validateGraph`/`consolidate` continuam antecipando feedback, mas erros
  exibidos vêm do backend.
- [ ] Foco por teclado preservado após render; `Escape` continua limpando seleção.
- [ ] Catálogo mostra status/executabilidade; DRAFT não executável.
- [ ] Nenhum prompt/resposta/segredo via `innerHTML`.
- [ ] `/chat/previa` continua isolada da API.
- [ ] Testes:
  - [ ] catálogo vindo da API;
  - [ ] salvar/carregar;
  - [ ] conflito 409/412;
  - [ ] validação/erros por nó;
  - [ ] foco e teclado;
  - [ ] `validateGraph`/`consolidate`;
  - [ ] sem `innerHTML`;
  - [ ] chat não envia `agentFlowId`.
- [ ] Revisão da responsável pelo componente de chat registrada no relatório.

## Arquivos prováveis

- `src/main/resources/static/public/js/pages/agent-plaza.js`
- `src/main/resources/static/public/js/pages/chat.js`
- `src/main/resources/static/modules/chat/views/index.ejs`
- `src/main/resources/static/public/css/pages/chat.css`
- `src/test/frontend/agent-plaza.test.js`
- `src/test/frontend/chat.test.js`

## Checks

- `node --test src/test/frontend/*.test.js`
- `./scripts/check` — somente se houver impacto em build; registrar se não aplicável.

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
