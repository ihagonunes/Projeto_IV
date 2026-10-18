# Auditoria da documentação — 2026-10-18

- **Base:** `b-ihago` @ `1a4e9ac`. O código de produção é o mesmo da verificação da Fase 0.
- **Método:**
  - leitura de toda a documentação versionada (cerca de 160 arquivos `.md`);
  - comparação com o código, as migrations, o `pom.xml`, o `compose.yaml`, o `.env.example` e o
    front.
- **Efeito:** nenhuma alteração em código. Foram ajustados só os packets da Sprint 3 (seção 4).

Severidade:

| Nível | Critério |
|---|---|
| 🔴 Alta | Induz um humano ou um agente a fazer algo errado ou inseguro |
| 🟠 Média | Informação falsa sobre o estado atual |
| 🟡 Baixa | Inconsistência menor ou arquivo fora de lugar |

---

## 1. Problemas encontrados

### 1.1 Guias de execução e de API (usados por humanos e agentes)

| # | Sev. | Arquivo | Problema | Evidência | Correção proposta |
|---|---|---|---|---|---|
| 1 | 🔴 | `docs/development/HOW_TO_RUN.md` §5 | O exemplo de `POST /api/v1/usuarios` envia `password`. O endpoint não aceita senha desde a "Simplificação 2", e esse endpoint será removido (decisão 3). | `ai/decisions.md` (Simplificação 2); `UserRequest` sem senha; TASK-S3-A7 | Trocar o exemplo pelo fluxo de convite. Depois da A4, documentar o bootstrap do admin em `POST /tenants`. |
| 2 | 🔴 | `README.md` (Modo 2) + `compose.yaml` | `docker compose up` não passa `JWT_SECRET` ao backend, e a app exige ≥ 32 caracteres para subir. O modo Docker do README **provavelmente falha no boot** (❓ inferido, não executado). | `compose.yaml` (env do backend sem `JWT_SECRET`); `JwtSecurityProperties.java:17` (`@Size(min=32)`); `application.yml` (`secret: ${JWT_SECRET:}`) | Adicionar `JWT_SECRET: ${JWT_SECRET}` (e as variáveis de demo opcionais) ao serviço `backend` **ou** documentar a variável. Mudança de config: precisa de aprovação. |
| 3 | 🔴 | `docs/development/HOW_TO_RUN.md` §2 | Diz "**Não há `docker-compose.yml`**", mas existe `compose.yaml`, citado no README. Os dois guias se contradizem. | `compose.yaml` na raiz | Unificar: README e HOW_TO_RUN descrevem o mesmo Modo Docker. |
| 4 | 🟠 | `docs/development/HOW_TO_RUN.md` (Estado atual) | Status desatualizados. | Real: 14 migrations; 717 unit + 83 IT (VERIFICACAO_FASE0 §1B) | Atualizar a tabela e os números, ou remover os números (envelhecem rápido). |
| 5 | 🔴 | `docs/development/CONTRATO_API_FRONT_BACK.md` | Documento de 27/09 com status "proposta". Desatualizado nos dois sentidos (front e backend). | Lista de chamadas do front (grep em `public/js`, `modules/*`) | Reescrever como contrato vigente. A TASK-S3-A10 já pede a atualização: antecipar a parte "estado atual" agora e deixar a parte nova para a A10. |
| 6 | 🟠 | `docs/development/BOOTSTRAP.md` | Texto do starter, já superado. | `SecurityBaselineConfig` (`anyRequest().authenticated()`); módulos implementados | Reescrever como "primeira execução" (env, `JWT_SECRET`, seed demo ou bootstrap do admin) ou apontar para o HOW_TO_RUN. |
| 7 | 🟠 | `docs/development/ENVIRONMENT.md` | Não cita Node.js/npm (front BFF), `JWT_SECRET` nem as variáveis do front. O Modo Docker aparece sem o front. | `package.json`; `.env.example` | Acrescentar o front e as variáveis obrigatórias. |

Detalhe do item 4 (`HOW_TO_RUN.md`):

| Afirmação no documento | Realidade |
|---|---|
| `aigateway` "em andamento" | catálogo, roteamento e política de modelos implementados |
| `observability` "planejado" | uso, dashboard e histórico implementados |
| migrations `V001`–`V011` | `V001`–`V014` |
| 149 + 64 testes | 717 unit + 83 IT |

Detalhe do item 5 (`CONTRATO_API_FRONT_BACK.md`):

| Afirmação no documento | Realidade |
|---|---|
| Módulos `department` e `aigateway` "não existem" | Existem e estão em uso |
| Identidade visual "pendente" | Implementada |
| `aceite-convite.js` usa o path errado | Já corrigido: `/v1/convites/aceitar` |
| Seção §2 lista só 10 endpoints | O backend expõe 33 |

Ainda quebrado hoje no front:
- `identidade-visual.js` chama `/tenant/identidade-visual` (singular) → 404;
- os controllers server-side chamam `/ia`, `/revisao`, `/revisao/confirmar`, `/auth/redefinir-senha`,
  `/usuarios/importar` e `GET /tenants`, que não existem no backend.

### 1.2 Documentos de contexto e de estado

| # | Sev. | Arquivo | Problema | Correção proposta |
|---|---|---|---|---|
| 8 | 🔴 | `docs/PROJECT_CONTEXT.md` (17/09) | Descreve um repositório que não existe mais (lista abaixo). Um agente que o leia como "estado atual" (o título promete isso) planeja errado. | Marcar como **SUPERADO** no topo, apontando para `VERIFICACAO_FASE0.md` e `ai/tasks/sprint-03/INDEX.md`, ou regenerar. |
| 9 | 🟠 | `docs/review/RELATORIO_STATUS_TASKS.md` (28/09) | Partes superadas (lista abaixo). | Nota no topo: "status de SPI-73/74/94 e front do dashboard superados por `VERIFICACAO_FASE0.md` §1C". |
| 10 | 🟠 | `docs/review/REVIEW_DEBUG_SPRINT2.md` | A tabela "Frontend × Backend — endpoints que não funcionam" está quase toda resolvida (só sobra a identidade visual); a linha de base "126 + 52" está desatualizada. | Nota de status no topo, com a lista do que ainda vale. |
| 11 | 🟡 | `ai/tasks/sprint-01/INDEX.md`, `ai/tasks/sprint-02/INDEX.md` e os packets | Os status são cópia do consolidado (o próprio INDEX avisa). Não há ponteiro para onde está o status real. | Uma linha em cada INDEX apontando para `RELATORIO_STATUS_TASKS.md` e `VERIFICACAO_FASE0.md`. |

O que o `PROJECT_CONTEXT.md` afirma e a realidade:

| Afirmação no documento | Realidade |
|---|---|
| Módulos `department`, `aigateway` e `observability` vazios | Implementados |
| `POST /usuarios` público, com senha | Protegido e sem senha |
| "Nenhum endpoint de negócio protegido" | 33 endpoints, quase todos protegidos |
| Só V001–V004 | V001–V014 |
| 16 arquivos de teste | Cerca de 800 testes |

O que está superado no `RELATORIO_STATUS_TASKS.md`:

| Afirmação no relatório | Realidade |
|---|---|
| Front do dashboard "não commitado" | Versionado |
| SPI-73 e SPI-74 "✅" | SPI-73 sem escopo (D5); SPI-74 com N05 aberto |
| SPI-94 "parcial por falta de UI" | O problema real é de autorização (D4) |

### 1.3 Documentação canônica (`ai/`)

| # | Sev. | Arquivo | Problema | Correção proposta |
|---|---|---|---|---|
| 12 | 🟠 | `ai/dependencies.md` | O catálogo omite dependências que **estão** no `pom.xml` e o front inteiro (lista abaixo). A política do próprio arquivo exige catálogo completo. | Incluir as dependências faltantes e uma seção "Frontend (npm)". Agora é só documentação; as tarefas C1 e E1 já tocam esse arquivo. |
| 13 | 🟡 | `ai/dependencies.md` | "OAuth2 Resource Server — usado quando IAM/JWT for implementado" (já está implementado). | Corrigir o texto. |
| 14 | 🟠 | `ai/architecture.md` §Segurança; `ai/testing.md`; ADR-30 | Dizem "login: `tenantSlug + email + password`". O ADR-32 tornou o `tenantSlug` opcional (resolução pelo domínio do e-mail), mas os três não citam a emenda. | Citar o ADR-32. **Incluído na TASK-S3-A10.** |
| 15 | 🟡 | `ai/architecture.md` (Persistência) + `db/migration/README.md` | Dizem "histórico em `historical_*`", mas a V014 usa o **schema** `historical.` (ex.: `historical.ai_usage_logs`). | Trocar para "schema `historical`". |
| 16 | 🟡 | `ai/architecture.md` | O módulo `platform` existe (`platform/config`) e não aparece na tabela de ownership nem na allowlist. | Acrescentar `platform` (config transversal, sem regra de negócio). |
| 17 | 🟠 | `ai/contracts.md` | Contratos listados como existentes que **não existem** no código (lista abaixo). | Separar "implementado" × "planejado", ou marcar os não implementados. Os contratos que mudam na Sprint 3 já têm tarefa (A10, B1, B2, C1, D1, E2). |
| 18 | 🟡 | `ai/decisions.md` (Decisões abertas) | As decisões 11–14 (provider da demo, retentativa/retenção, custo de erro, RF047 com negadas) só existem no `INDEX.md` da Sprint 3. | Registrar em "Decisões abertas", com ponteiro para o INDEX. |
| 19 | 🟡 | ADR-04, ADR-33, ADR-34 | Continuam com o texto antigo (ADMIN = conjunto de permissões; escopo "não persistido na tupla"), sem aviso de que os ADR-36/37 os emendam. | Já previsto na TASK-S3-A10. Até lá, um aviso de uma linha em cada ADR evitaria leitura errada. |

Detalhe do item 12 (`ai/dependencies.md`):
- **Ausentes do catálogo, presentes no `pom.xml`:** `springdoc-openapi-starter-webmvc-ui 3.1.0` e
  `flyway-database-postgresql`.
- **Ausentes do catálogo, front:** `express`, `ejs`, `axios`, `multer`, `lru-cache` e as demais do
  `package.json`.

Detalhe do item 17 (`ai/contracts.md`):

| Contrato listado | Situação real |
|---|---|
| `AICompletionService` | não existe (TASK-S3-C1) |
| `UsageMetricsQueryPort`, `CostMetricsQueryPort` | `observability/api` está vazio; existem só portas internas |
| Eventos `UserProvisioned` e `UserDeactivated` ("definitivos do MVP") | não implementados |
| `ProjectQueryPort` e todo o `engagement`/`governancescore` | não existem |
| Seção Completion: `CompletionCommand` com `departmentId` vindo do chamador | contradiz o ADR-38 (resolvido no servidor) |

### 1.4 Convenções de Git e PR

| # | Sev. | Arquivo | Problema | Correção proposta |
|---|---|---|---|---|
| 20 | 🟠 | `.github/pull_request_template.md` | O `AGENTS.md` §13 e o `copilot-instructions.md` exigem segurança, isolamento de tenant e critérios de aceite na descrição do PR, mas o template não tem essas seções. O campo Jira vem preenchido com "SM_Projeto_IV" em vez da chave. | Acrescentar "Jira: SPI-xx", "Critérios de aceite", "Segurança / isolamento de tenant" e "CI". |
| 21 | 🟡 | `.github/copilot-instructions.md` × `AGENTS.md` §12 | O Copilot permite o tipo `style`, que o `AGENTS.md` não lista. | Alinhar os dois (adicionar `style` ao AGENTS ou removê-lo do Copilot). |
| 22 | 🟡 | `AGENTS.md` §12 × prática | As branches reais (`b-ihago`, `IhanDevelopment`, `b-test-coverage`) não seguem `<type>/<JIRA-KEY>-descricao`. | Decisão do time: cumprir a regra nas próximas branches (o HANDOFF já exige) ou flexibilizar o texto. |

### 1.5 Arquivos fora de lugar

| # | Sev. | Arquivo | Problema | Correção proposta |
|---|---|---|---|---|
| 23 | 🟡 | `Projeto_IV/.idea/workspace.xml` | Arquivo de IDE versionado dentro de uma subpasta com o nome do projeto. | Remover do Git e incluir no `.gitignore` (aprovação). |
| 24 | 🟡 | `3d_quantum_neural_network.txt` (raiz, 1.289 linhas) | Protótipo HTML salvo como `.txt` na raiz, sem referência no código. A view usa `public/js/pages/neural-network.js`. | Mover para fora do repo ou para `docs/design/` (aprovação). |
| 25 | 🟡 | `scripts/db/fix-crossmodule.sql` | SQL manual fora do Flyway, contra tabelas `public.historical_*` e `project_projects`, que **nenhuma** migration cria. Descreve outro layout de banco. | Marcar como obsoleto, ou mover para `docs/`, explicando o contexto (já registrado em D18). |

### 1.6 Documentos históricos

| # | Sev. | Arquivo | Problema | Correção proposta |
|---|---|---|---|---|
| 26 | 🟡 | `docs/lessonslearned&Decisions.md` | Registro da Sprint 1. A decisão D1 diz que não existe endpoint de criação de usuário (depois foi criado e agora vai ser removido). O nome do arquivo tem `&`, o que dificulta o uso em shell e em URL. | Manter como histórico, com o cabeçalho "Registro da Sprint 1 — não reflete o estado atual". Renomear é opcional. |

---

## 2. O que está consistente (sem ação)

- `ai/context-map.md`, `ai/project.md`, `ai/epics/*`: genéricos, mas não contradizem o código.
- `docs/development/OBSERVABILITY_USAGE_SCHEMA.md`: bate com a V013, a V014 e o
  `HistoricalUsageAdapter`. Ressalva: a seção "API de consulta" não diz que o endpoint está sem
  escopo (D5); isso será corrigido pela TASK-S3-A3.
- `ai/decisions.md`: índice de ADRs completo (01–39) e status coerentes com os arquivos.
- CI (`.github/workflows/ci.yml`): roda os mesmos comandos que o README e o `testing.md` descrevem.
- Pacote `ai/tasks/sprint-03/`: numeração de migrations, chaves Jira (SPI-169–172), dependências e
  referências cruzadas conferidas. Corrigido nesta auditoria: §4.

## 3. Ordem sugerida de correção

| Prioridade | Itens | Quem faz | Precisa de aprovação? |
|---|---|---|---|
| 1 — antes de passar ao outro modelo | 8 (marcar `PROJECT_CONTEXT` como superado), 1, 3, 5 (estado atual), 9, 10, 19 (avisos de uma linha) | Planejador (só docs) | Não — são correções documentais |
| 2 | 2 (`compose.yaml` com `JWT_SECRET`) | Implementador | **Sim**: muda configuração |
| 3 | 4, 6, 7, 11–18, 20–21 | Planejador | Não |
| 4 | 22–26 | Time | Sim: decisão de convenção ou remoção de arquivos |

## 4. Correções aplicadas nesta auditoria (só no pacote da Sprint 3)

- **TASK-S3-C1:** passa a exigir:
  - atualização de `ai/contracts.md` (Completion);
  - emendas aos ADR-11 e ADR-13, que o ADR-38 pressupõe e nenhuma tarefa cobria.
- **TASK-S3-B1/B2:** passam a exigir a atualização de `ai/contracts.md` (`DepartmentBudgetQuery`,
  `BudgetStatusQueryPort`).
- **TASK-S3-A10:**
  - inclui o item 14 (citar o ADR-32);
  - texto ajustado: os ADRs 36/37 já estão ACCEPTED.

## 5. Prioridade 1 aplicada (2026-10-18)

| Item | Arquivo | O que foi feito |
|---|---|---|
| 8 | `docs/PROJECT_CONTEXT.md` | Aviso **SUPERADO** no topo, com ponteiros para o estado atual; conteúdo mantido como histórico. |
| 1 | `docs/development/HOW_TO_RUN.md` | O exemplo de `POST /usuarios` com senha virou o fluxo de convite, com a limitação N01 explícita. "Problemas comuns" e "Importante" alinhados. |
| 3 | `docs/development/HOW_TO_RUN.md` | O Modo B passa a descrever o `compose.yaml`, com o aviso do `JWT_SECRET` (item 2). O container avulso foi mantido. |
| 4 | `docs/development/HOW_TO_RUN.md` | Status dos módulos, V001–V014 e 717 + 83 testes; aviso do N01. |
| 5 | `docs/development/CONTRATO_API_FRONT_BACK.md` | §2 reescrita com os 33 endpoints vigentes e as chamadas reais do front; §3–§7 rotuladas como registro histórico de 27/09. |
| 9 | `docs/review/RELATORIO_STATUS_TASKS.md` | Nota de status no topo (SPI-73/74/94, front do dashboard, gap N01). |
| 10 | `docs/review/REVIEW_DEBUG_SPRINT2.md` | Nota de status no topo (linha de base, endpoints do front, decisões A1/M4). |
| 19 | ADR-04, ADR-33, ADR-34 | Aviso de uma linha ("emendado pelos ADR-36/37"). O Status e o texto original não foram alterados; a emenda formal continua na TASK-S3-A10. |

Itens que continuam abertos: 2 (`compose.yaml`; exige aprovação), 6, 7, 11–18, 20–26.
