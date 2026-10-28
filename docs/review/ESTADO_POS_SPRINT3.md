# Estado do projeto após a Sprint 3 — base para a Sprint 4

- **Data:** 2026-10-27.
- **Base:** `main` @ `a046384` (merge do PR #23, `b-evelyn`). A `origin/main` está igual.
- **Método:** somente leitura. Etapas:
  - `git log 6d30c60..HEAD`;
  - leitura dos documentos novos;
  - conferência pontual do código;
  - execução das suítes (§3);
  - consulta ao Jira (projeto SPI).
- **Nenhum código alterado.**

## 1. O que a Sprint 3 entregou (por commit)

| Plano (`ai/tasks/sprint-03`) | Commit(s) | Situação |
|---|---|---|
| A1 V015 + concessões | `af67bc3` | ✅ |
| A2 avaliador e gate fechado | `a64f080`, `3d4d53f` | ✅ (revisão: F1 crítico corrigido) |
| A4 bootstrap e fixtures | `73788e1` | ✅ (`iam/support/TestTenants.java`) |
| A5 convite com concessão | `7edd2ac` | ✅ |
| A6 papéis e concessões | `adcf28f` | ✅ |
| A7 guardas de usuários | `b505e19` | ✅ (F3 lock pessimista: `TenantAdminGuard`) |
| A8 remoção do legado | `7b00539` | ✅ (a tabela `user_permission` ainda existe; o drop é da Sprint 4) |
| A3 escopo em observability e deptos | `07774db` | ✅ |
| A9 front de autorização | `b69972f`, `f28a271` | ✅ |
| A10 docs da autorização | `a1fdf49` | ✅ (`MATRIZ_PERMISSOES.md`) |
| B1–B3 orçamento | `40c3240`, `38b4d5f`, `9faf00a` | ✅ |
| C1–C3 tentativas e provider | `d892ba2`, `80c7a22`, `f5bb37a` | ✅ (STUB + OpenAI-compatível) |
| D1 backend do chat | `c45a36f` | ✅ |
| D2 UI do chat (Lyn) | `3d86778` | ✅, com pendências de backend (`CHAT_FRONTEND_BACKEND_PENDENCIAS.md`) |
| E1–E2 agente de políticas | `819107d`, `f9119ec` | ✅ (busca nativa, opção A) |
| F1–F2 dashboard | `5c60442`, `37cb206` | ✅ |
| Revisão de convite de outro tenant (F6) | `1e28fc4` | ✅ (404) |

### Fora do plano (decisões tomadas durante a sprint)

- **V020 `legacy_identity_links`:** vínculos entre usuários e departamentos atuais e as identidades
  do histórico (NexoCode). Emenda ao ADR-26.
- **V021 `separate_administration_from_analytics`:** o `TENANT_ADMIN` perdeu `USAGE:READ` e
  `GOVERNANCE_SCORE:READ` (são 18 permissões). Emenda ao ADR-30.
- **Sessão do navegador por cookie HttpOnly no BFF**, com CSRF por header. Emenda ao ADR-30.
- **Dashboards por perfil** (`e800c2b`). O protótipo `6c77c72` foi revertido (`8e58694`); a branch
  `feature/dashboards-atlas` tem 1 commit não integrado (`7bfb5d5`).
- **Documentos novos:**
  - `docs/development/`: `BANCO_E_IMPORTACAO_HISTORICA`, `DASHBOARD_E_ORCAMENTOS`,
    `DASHBOARD_INDICADORES_E_CALCULOS`, `VALIDACAO_E_PENDENCIAS`,
    `CHAT_FRONTEND_BACKEND_PENDENCIAS`, `MATRIZ_PERMISSOES` e `ESCALA_VISUAL`;
  - relatórios HTML em `docs/`.

## 2. Pendências verificadas no código (entram na Sprint 4)

| # | Pendência | Evidência | Tarefa S4 |
|---|---|---|---|
| 1 | `engagement`, `governancescore` e `project` vazios | 0 arquivos `.java` em cada módulo | E1–E6 (engagement) |
| 2 | Preço nulo → custo `KNOWN = 0` | `aigateway/domain/model/Model.java:147-157` | P1 |
| 3 | Provider real da demo não escolhido | sem configuração além do STUB; decisão 11 aberta | P1 (S4-D1) |
| 4 | Tela do catálogo chama rotas inexistentes `/api/v1/ia/*` | `public/js/pages/onboarding/step-ia.js:79,110,189` | P2 |
| 5 | Sem `GET` da política de modelos do depto | só `PUT /ai-gateway/departments/{id}/model-policy` | P2 |
| 6 | Chat: sem PATCH/DELETE, sem recibo ao recarregar, sem lista de modelos do usuário, sem idempotência | `ConversationStatus` = ACTIVE/ARCHIVED; `MessageResponse` só com `requestId`; `CHAT_FRONTEND_BACKEND_PENDENCIAS.md` §4 | C1–C3 |
| 7 | Projeção de orçamento inexistente | nenhuma classe de projeção no `observability` | B1 |
| 8 | `observability/api` vazio | o diretório só tem `.gitkeep` | E1 (primeiro contrato) |
| 9 | Tabela `user_permission` ainda existe | V004; não mapeada desde a A8 | H2 |
| 10 | `compose.yaml` sem `JWT_SECRET` | serviço `backend` sem a variável | H1 |
| 11 | Testes do front fora do CI | `.github/workflows/ci.yml` só tem Maven | H1 |
| 12 | Sem execução integral verde após a V021 | `VALIDACAO_E_PENDENCIAS.md` ("não houve uma nova execução integral única verde") | H1 — ver §3 |

## 3. Suítes reexecutadas em 27/10/2026

| Suíte | Comando | Resultado |
|---|---|---|
| Unit + ArchUnit, **sem `clean`** | `mvn -B test` | ❌ 856 executados, 3 falhas e 18 erros. **Causa:** classes de teste removidas que ficaram em `target/` (`AuthorizationQueryAdapterTest`, `RegisterUserTest`) mais o ArchUnit lendo classes obsoletas. Não é regressão do código. |
| Front | `node --test src/test/frontend/*.test.js` | ❌ antes do `npm ci` (falta de `express`/`dotenv`); ✅ **64/64** depois do `npm ci` |
| Unit + IT, **com `clean`** | `MAVEN_ARGS=-Dspring.test.context.cache.maxSize=1 mvn -B clean verify -Pintegration` | Unit + ArchUnit **817/817 ✅**. Integração **168/171**: as 3 falhas são `HistoricalLoaderIT`, `LegacyIdentityImportIT` e `LocalSetupIT`, todas com `spawn psql ENOENT`. O cliente `psql` não está instalado nesta máquina; é o pré-requisito documentado em `VALIDACAO_E_PENDENCIAS.md` ("PostgreSQL real/psql"). **Não é defeito de código**, mas a execução integral verde continua não comprovada (H1). |

Lições incorporadas ao `ai/tasks/sprint-04/HANDOFF.md` §4:
- sempre `clean`;
- `npm ci` antes dos testes do front.

## 4. Jira (consultado em 27/10/2026)

**SPI Sprint 3 (ainda ativa):** 61 tarefas, 59 concluídas.
- SPI-128 e SPI-130 estão "A fazer", mas foram implementadas (`819107d`, `f9119ec`). Fechar.

**SPI Sprint 4 (futura, 26/10–06/11):** 22 tarefas. Leitura contra o código:

| Situação | Tarefas |
|---|---|
| Duplicatas do que já foi entregue | SPI-102 a 110 (= SPI-118 a 126) |
| Descrições que contradizem decisões aceitas | SPI-137 pede pgvector/Spring AI (o ADR-35 aceitou a opção A); SPI-139/145/147 reservam V018/V019/V020, já usadas; SPI-140 usa rotas em inglês |
| Parcialmente entregues | SPI-133 (o recibo existe no envio), SPI-138 (o adaptador existe), SPI-139/140 (o chat existe) |
| Trabalho novo de fato | SPI-87, 131, 141–148 |

O tratamento de cada uma está em `ai/tasks/sprint-04/INDEX.md` §6.

**SPI Sprint 5 (futura):** SPI-149 a 168 (ranking, score, dashboard executivo, E2E, CI/CD, demo).
Não entram neste planejamento.
