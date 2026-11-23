# E2E dos agentes com Playwright — runbook e seed

Status: **planejado (BE-07)**. Este documento descreve o ambiente, o seed
descartável e o roteiro de execução no navegador. Nada aqui é usado pelo
runtime do produto.

## 1. Decisões

- **Opção B:** a execução é feita pela UI da praça (input + resultado), não só
  por chamada de API.
- **Provider STUB** (`kind=STUB`, modelo `stub-echo`): offline e determinístico.
- **Playwright MCP**, sem adicionar `@playwright/test`/`playwright` ao
  `package.json`.
- **Seed descartável** via API pública em `scripts/e2e/seed-agents-e2e.mjs`.
- **Banco descartável** `qsoft_e2e`; nenhum dado de produção.
- O erro de **SRI do Bootstrap** no console é pré-existente/global e fica fora
  do aceite da praça.

## 2. Pré-requisitos

| Item | Valor |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| Node | 22+ (fetch nativo; sem dependência nova) |
| PostgreSQL | 18 local **ou** Docker |
| Backend | `127.0.0.1:8081` (8080 fica livre) |
| Front | `127.0.0.1:3100` com `API_PROXY_TARGET=http://127.0.0.1:8081` |
| Banco | `qsoft_e2e` (descartável) |
| Playwright | MCP já disponível na sessão |

## 3. Banco e .env

1. Crie o banco descartável:
   ```sql
   CREATE DATABASE qsoft_e2e;
   ```
2. Crie um `.env` local (não commitar) com:
   ```dotenv
   DB_HOST=localhost
   DB_PORT=5432
   DB_USER=<usuario>
   DB_PASSWORD=<senha>
   DB_NAME=qsoft_e2e
   SERVER_PORT=8081
   JWT_SECRET=<48+ caracteres aleatórios>
   SETUP_MOCK_PASSWORD=<8-72 bytes>
   NODE_ENV=development
   ```
3. `scripts/setup-local.js` (com `--skip-build false`) compila o JAR, aplica o
   Flyway (V001–V030) e cria o tenant `qsoft-local`, admin e contas por papel
   no `work-dir` (`accounts.local.json`). Ele inicia o backend durante o setup;
   ao final, garanta que o backend esteja rodando em `:8081` (reinicie o JAR se
   necessário).

## 4. Front

```powershell
$env:PORT = "3100"
$env:IP = "127.0.0.1"
$env:API_PROXY_TARGET = "http://127.0.0.1:8081"
npm start
```

O BFF encaminha `/api` e injeta o cookie de sessão; sem `API_PROXY_TARGET` o
front fala com 8080 e o live falha.

## 5. Seed (`scripts/e2e/seed-agents-e2e.mjs`)

### Entradas

| Variável | Uso |
|---|---|
| `E2E_BASE_URL` | default `http://127.0.0.1:8081` |
| `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` | cria provider/modelo/política |
| `E2E_EXECUTIVE_EMAIL` / `E2E_EXECUTIVE_PASSWORD` | salva o orçamento (`DEPARTMENT_BUDGET:MANAGE`) |
| `E2E_DEPARTMENT_ID` | opcional; senão lista/cria um departamento |
| `--dry-run` | imprime o plano de chamadas e não executa |

As contas vêm do `work-dir` do `setup-local` (`accounts.local.json`).

### Passos (idempotentes por nome)

1. **Login admin** → `POST /api/v1/auth/login`.
2. **Departamento**: `GET /api/v1/departamentos`; se vazio,
   `POST /api/v1/departamentos` (`{name:"E2E Agentes"}`).
3. **Provider STUB** (se não existir `e2e-stub`):
   `POST /api/v1/ai-gateway/providers`
   `{ "name": "e2e-stub", "kind": "STUB" }`.
4. **Modelo** (se não existir):
   `POST /api/v1/ai-gateway/models`
   `{ "providerId":"<id>", "name":"e2e-stub-echo",
      "externalName":"stub-echo",
      "inputPricePer1k":"0.01", "outputPricePer1k":"0.03" }`.
5. **Política do departamento**:
   `PUT /api/v1/ai-gateway/departments/{id}/model-policy`
   `{ "allowedModelIds":["<modelId>"], "defaultModelId":"<modelId>" }`.
6. **Orçamento do mês** (login **executivo**):
   - `POST /api/v1/orcamento-ia/sugestao` com o plano rascunho:
     ```json
     {
       "amount": 10000.00,
       "departments": [{ "departmentId": "<id>", "percentage": 100.00 }],
       "allocations": [],
       "weights": { "COLLABORATOR": 1, "LEADER": 1, "MANAGER": 1, "EXECUTIVE": 1 },
       "rates": [{ "modelId": "<modelId>", "currency": "BRL",
                   "exchangeRate": 1, "reference": "E2E" }]
     }
     ```
   - `PUT /api/v1/orcamento-ia/{YYYY-MM}` com o plano devolvido.
7. Imprime apenas IDs/nomes criados; nunca token/senha.

## 6. Roteiro Playwright (MCP)

Pré-condição: backend 8081, front 3100, seed executado.

1. `browser_navigate http://127.0.0.1:3100/login`.
2. Preencher e-mail/senha do **admin** (ou manager) e entrar; aguardar `/chat`.
3. Clicar em **Agente** e snapshot:
   - catálogo com **17** itens;
   - `DRAFT` (ex.: Código) desabilitado com rótulo "(DRAFT, não executável)";
   - `Consolidador` sintético presente.
4. Montar o fluxo de 5 agentes:
   `Tarefa → policies/writing → merge1 → data; Tarefa → research;
    data/research → merge2 → review → Resultado`.
5. **Salvar rascunho**; ler `data-flow-id` do root (contrato de teste) e
   navegar para `/chat?fluxo=<id>`; confirmar que o grafo recarregou.
6. **Confirmar esteira** (validar + publicar). Confirmar `data-flow-status=PUBLISHED`.
7. Preencher o input e clicar em **Executar esteira**:
   - status `SUCCEEDED`;
   - resultado visível (contém `[stub]`);
   - 9 etapas; 5 agentes com `requestId`; custo `KNOWN` > 0.
8. Screenshot do resultado e coleta do JSON de `GET /execucoes/{id}` (sem token).
9. Console: nenhum erro da praça (ignorar o SRI do Bootstrap e o favicon 404).

### Opcionais

- Duas abas/sessões: salvar/publicar em uma e tentar salvar a outra → 409/412.
- Tenant B (criar outro tenant) → `GET /execucoes/{id}` e `POST` → 404.
- Preview `/chat/previa`: sem seção de execução e sem chamadas `/api`.

## 7. Evidências e limpeza

- Salvar screenshot em `%TEMP%\opencode\e2e-*.png` e o JSON da execução no
  relatório (sem tokens).
- Encerrar backend e front (`taskkill /T`), remover `.playwright-mcp/` do repo
  e apagar o banco `qsoft_e2e` (ou recriá-lo na próxima execução).

## 9. Execução registrada em 22/11/2026

- **Ambiente:** PostgreSQL descartável `qsoft-e2e` (Docker, porta 55432), backend
  8081, front 3100, tenant `qsoft-local`, conta **gestor** (`gestor.teste@qsoft.local`).
- **Fluxo:** `9b49442a-38c8-455a-8a8c-c6f6ebe5bdf7` — 5 agentes
  (`policies`, `writing`, `data`, `research`, `review`) + 2 merges + result,
  criado/publicado via API para determinismo e carregado na UI por
  `/chat?fluxo=<id>`. A UI exibiu o catálogo live, o fluxo publicado e a seção
  **Execução**.
- **Resultado:** `SUCCEEDED`; texto `[stub] …`; **9 etapas** (5 agentes com
  `requestId` e recibo `KNOWN`); custo agregado `KNOWN (0.00828)`.
- **Banco:** 5 tentativas `AGENT_FLOW`/`SUCCEEDED`; execução `SUCCEEDED`;
  5 etapas com `request_id`; 9 etapas no total.
- **Evidência:** screenshot em
  `%TEMP%\opencode\e2e-execucao.png` (estado com resultado/etapas/custo).
- **Observações operacionais:**
  - o front precisa de `SPRING_API_URL=http://127.0.0.1:8081`,
    `API_PROXY_TARGET=http://127.0.0.1:8081` e `QSOFT_TENANT_SLUG=qsoft-local`;
  - se `APP_BOOTSTRAP_DEMO_DATA=true` vier do `.env`, o backend também cria o
    tenant demo `qsoft`; sem o override do slug o login envia `qsoft` e recebe
    401 — use `qsoft-local`;
  - o cache-bust do JS foi `spi-180-execucao-2`;
  - o erro de SRI do Bootstrap continua pré-existente e alheio à praça;
  - a montagem por cliques foi validada no E2E da prévia; o fluxo live foi
    criado/publicado por API para isolar a execução.

## 11. Cobertura de verificação (23/11/2026)

| Ponto | Evidência |
|---|---|
| Conflito 412 no save (live) | Fluxo DRAFT aberto na UI (rowVersion 1); PUT concorrente via API → 2; “Salvar rascunho” → 412; aviso Recarregar/Sobrescrever; “Sobrescrever” recuperou e salvou. |
| Conflito 412 na publicação | Teste DOM `conflito 412 na publicação...`: aviso exibido, status não vira PUBLISHED e a execução não aparece. |
| Isolamento (live) | Admin do Tenant B abriu `/chat?fluxo=<fluxo do A>`: `AGENT_FLOW_NOT_FOUND`, sem nome/grafo do A, só o nó `task`, execução oculta. |
| Concorrência real | `AgentsFlowConcurrencyIT` (3): duas atualizações simultâneas → 1 vence; duas versões simultâneas → 1 inserida; duas execuções com a mesma Idempotency-Key → 1 linha. |
| Drag-and-drop real | Playwright `dragTo` do cartão “Código” para `.plaza-canvas`: nó criado, “1 agentes · 0 conexões”. |
| Idempotência/clique duplo | API IT + DOM: duas chamadas síncronas → 1 requisição. |
| Execução live | `SUCCEEDED`, 9 etapas, 5 recibos `KNOWN`, custo `0.00828`. |

## 12. Decisão pendente: Playwright no CI

1. **Ad-hoc via MCP (atual)** — sem dependência; executado manualmente e
   documentado. Prós: respeita a política de dependências e não adiciona
   flakiness ao CI. Contras: não há regressão automática de UI.
2. **Adicionar `@playwright/test`** — devDependency + job de CI com Chromium.
   Prós: E2E versionado e bloqueante. Contras: exceção à política de
   dependências, instalação de browser no CI, manutenção/flakiness.
3. **Selenium/Java (já previsto no plano)** — alternativa sem npm, mesma
   toolchain do backend. Prós: sem dependência Node; Contras: setup de WebDriver
   e browser no CI, mais verboso.
4. **Smoke de API em CI (Node `fetch`) + testes DOM** — cobre contrato e
   integração de componentes sem browser. Prós: barato e estável; Contras: não
   cobre o E2E real (drag, foco, diálogo).

Recomendação: manter (1) nesta fase e decidir (2) quando o frontend estabilizar,
com um job noturno (não bloqueante em PR) para reduzir flakiness.

## 13. Limites conhecidos

- Execução síncrona (até 120 s); sem polling nem cancelamento por endpoint.
- Sem retry automático de transitórias (BE-06).
- `result_text` guarda o resultado final; retenção/LGPD pendente.
- Sem integração com o chat (`agentFlowId`).
- Sem `@playwright/test` versionado: o roteiro é executado pelo MCP e
  documentado; transformar em teste de CI exige decisão de dependência.
