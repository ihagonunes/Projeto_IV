# TASK-AGENTS-BE-07 — Execução no frontend, seed E2E e Playwright

## Metadados

- **Jira:** SPI-180.
- **Depende de:** BE-05, BE-06 e ADR-45 `ACCEPTED`.
- **Módulo:** frontend (`static/public/js/pages`) + `scripts/e2e/` (descartável).
- **Migrations:** nenhuma.
- **Dependência nova:** **nenhuma**. E2E via Playwright MCP; seed com `fetch`
  nativo do Node.

## Objetivo

Fechar o caminho ponta a ponta no navegador: montar/publicar um fluxo com ~5
agentes, executar com uma entrada, ver o resultado/etapas/custo e validar tudo
com Playwright sobre um tenant descartável semeado com provider STUB.

## Fora de escopo

- integração com o turno do chat (`agentFlowId`);
- execução assíncrona/polling;
- retry de transitórias e limite de concorrência (pendências do BE-06);
- provider real, nova permissão, novo endpoint;
- alterar o catálogo/V029 ou o chat.

## Critérios de aceitação

### A. Frontend (`agent-plaza.js`)

- [ ] Fora da prévia, após publicar, exibir a seção **Execução** somente quando
  houver `service` e o fluxo estiver `PUBLISHED`. O diálogo **não** deve fechar
  na publicação; `onConfirm`/fechamento passam a ocorrer após execução
  bem-sucedida (ou por ação explícita de fechar).
- [ ] Campo de entrada (1–8000 chars) e botão **Executar esteira**.
- [ ] `POST /agentes/fluxos/{id}/execucoes` com `Idempotency-Key` (UUID/8–80);
  botão desabilitado enquanto executa; nova tentativa após `FAILED`/`TIMED_OUT`
  usa **nova** chave (o replay da mesma chave devolve a execução anterior).
- [ ] Exibir `status`, `result` (textContent), etapas (`nodeId`, `nodeKind`,
  status, `requestId`, custo do recibo) e custo agregado (`KNOWN`/`INCONCLUSIVE`/`NOT_APPLICABLE`).
- [ ] 400/403/404/409/412/422 com mensagem clara; sem stack trace.
- [ ] Após publicar, `rowVersion`/`status` atualizados; a execução usa o
  `flowId` corrente (criado ou carregado por `/chat?fluxo=<id>`).
- [ ] Prévia (`/chat/previa`) permanece sem execução e sem API.
- [ ] Expor no root, fora da prévia, `data-flow-id`, `data-flow-status` e
  `data-row-version` (contrato de testabilidade/reload do E2E).
- [ ] Nenhum `innerHTML`; nenhum prompt/resposta no console; foco/Escape preservados.
- [ ] Testes de DOM com client falso: sucesso com resultado/etapas/custo,
  erro 422, falha 409/412, botão desabilitado durante a chamada, prévia sem seção.

### B. Seed descartável (`scripts/e2e/seed-agents-e2e.mjs`)

- [ ] Lê `E2E_BASE_URL`, `E2E_ADMIN_EMAIL/PASSWORD`, `E2E_EXECUTIVE_EMAIL/PASSWORD`
  (sem segredo no código).
- [ ] Idempotente por nome (`e2e-stub-*`): provider `STUB` + modelo
  `stub-echo` (preços > 0) + `ModelPolicy` do departamento (default).
- [ ] Configura o orçamento do mês corrente com o **executivo** (sugestão +
  `PUT /orcamento-ia/{YYYY-MM}`), pesos por papel e taxa BRL.
- [ ] Não imprime token/senha; falha com exit code ≠ 0 e mensagem curta.
- [ ] `--dry-run` imprime o plano de chamadas sem executar.
- [ ] Documentado em `docs/development/E2E_AGENTES_PLAYWRIGHT.md`; não faz parte
  do build/runtime.

### C. E2E (Playwright MCP)

- [ ] Stack: banco descartável, backend 8081, front 3100 com
  `API_PROXY_TARGET=http://127.0.0.1:8081`.
- [ ] Login pela UI com a conta semeada; abrir a praça.
- [ ] Catálogo live com 17 itens; `DRAFT` desabilitado.
- [ ] Montar o fluxo de 5 agentes (policies, writing, data, research, review) +
  2 merges + result; salvar; recarregar por `/chat?fluxo=<id>`.
- [ ] Validar (estado válido) e publicar.
- [ ] Executar com input; ver `SUCCEEDED`, resultado, 9 etapas, recibos e custo `KNOWN`.
- [ ] Evidências: screenshot do resultado, JSON da execução (sem token) e
  console sem erros da praça (o erro de SRI do Bootstrap é conhecido/pré-existente).
- [ ] Opcional: conflito 412 com duas sessões; Tenant B não executa (404).
- [ ] Limpeza: derrubar processos, remover `.playwright-mcp/` e apagar o banco.

## Arquivos prováveis

- `src/main/resources/static/public/js/pages/agent-plaza.js`
- `src/test/frontend/agent-plaza*.test.js`
- `scripts/e2e/seed-agents-e2e.mjs`
- `docs/development/E2E_AGENTES_PLAYWRIGHT.md`
- `ai/testing.md`, `ai/tasks/agents/INDEX.md` (estado)

## Decisões registradas

- **Opção B:** implementar a UI de execução antes do E2E; o resultado é visto na tela.
- **STUB** + **Playwright MCP**; nenhuma dependência npm/Maven nova.
- **Seed descartável** por API (sem SQL direto), usando as contas do `setup-local`.
- Banco `qsoft_e2e` descartável; nenhum dado de produção.
- O erro de **SRI do Bootstrap** é pré-existente e global; não bloqueia o BE-07.
  Corrigi-lo é tarefa separada (fora do escopo).

## Checks

- `node --test src/test/frontend/*.test.js`
- Seed: `--dry-run` no CI/local e execução real só no banco descartável.
- E2E: execução via MCP, com evidências no relatório.
- `./scripts/check` / `check-integration` somente se houver mudança de backend
  (não prevista).

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
