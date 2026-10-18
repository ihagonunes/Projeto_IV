# Relatório de Status das Tasks — SM_Projeto_IV (SPI)

> **Nota de status (2026-10-18).** Partes deste relatório foram superadas por
> `docs/review/VERIFICACAO_FASE0.md` §1C (reconciliação da Sprint 3 contra o código):
>
> | Item | Este relatório diz | Situação real |
> |---|---|---|
> | SPI-73 | ✅ | O endpoint existe, mas **não aplica escopo** (D5); fecha com a TASK-S3-A3. |
> | SPI-74 | ✅ | Continua fechada, mas o N05 segue aberto; tratado na SPI-171. |
> | SPI-94 | 🟠 parcial por falta de UI | O problema real é de autorização (D4: gestor sem depto vê o tenant). |
> | SPI-92/93/95 | Front "não commitado" | Os arquivos do dashboard estão versionados. |
> | Gap "convidados sem permissão" (§8, item 3) | — | Confirmado (N01); tratado em SPI-169 e SPI-46. |

> **Data de referência:** 2026-09-28
> **Branch analisada:** `IhanDevelopment`
> **Baseline de código:** `ce927f0` (2026-09-27) + alterações não commitadas do dashboard
> **Escopo:** task packets de `ai/tasks/` (Sprint 1 e Sprint 2) cruzados com evidência
> real de implementação (histórico Git, código-fonte, migrations, testes).

---

## 1. Metodologia e fontes

Este relatório foi produzido por leitura de repositório, sem alteração de código.

Fontes utilizadas:

1. `ai/tasks/sprint-01/INDEX.md` e `ai/tasks/sprint-02/INDEX.md`;
2. task packets em `ai/tasks/sprint-01/**` e `ai/tasks/sprint-02/**`;
3. histórico Git (`git log --oneline --all`);
4. código-fonte (`src/main/java`, `src/main/resources/static`);
5. migrations Flyway (`src/main/resources/db/migration`);
6. testes (`src/test/java`);
7. `ai/decisions.md` (decisões abertas), `docs/development/CONTRATO_API_FRONT_BACK.md`
   e `docs/review/REVIEW_DEBUG_SPRINT2.md`.

> **Aviso sobre o campo `Status` dos task packets.**
> O campo `Status`/`Status no consolidado` de cada task packet é uma **cópia do
> `consolidado.md` original** e **não foi sincronizado** com o que foi efetivamente
> implementado. O próprio `ai/tasks/sprint-01/INDEX.md` registra: *"Os status deste
> diretório reproduzem o `consolidado.md` recebido. Não são sincronizados
> automaticamente com os Jiras."* Por isso, o status abaixo é **inferido de
> evidência de implementação**, não do Jira.

### Legenda

| Símbolo | Significado |
|---|---|
| ✅ | **Concluída** — implementada e com evidência no código/testes |
| 🟠 | **Parcial** — parte entregue, parte pendente (detalhada na seção 5) |
| ⛔ | **Pendente** — sem implementação (detalhada na seção 6) |
| ⛔🔒 | **Pendente bloqueada** — depende de decisão arquitetural/ADR |

---

## 2. Resumo quantitativo

| Categoria | Qtde |
|---|---:|
| Sprint 1 — Produto/Fundação | 7 (7 ✅) |
| Sprint 1 — Entregáveis acadêmicos | 9 (8 ⛔, 1 em progresso) |
| Sprint 2 — Produto | 64 |
| ├─ ✅ Concluídas | 58 |
| ├─ 🟠 Parciais | 6 (SPI-57, SPI-92, SPI-93, SPI-94, SPI-95) — ver seção 5 |
| └─ ⛔ Pendentes | 4 (SPI-48, SPI-51, SPI-56, SPI-65 🔒) — ver seção 6 |

---

## 3. Sprint 1

### 3.1 Produto / Fundação — 7 tasks (✅ 7/7)

| Task | Descrição | Status | Evidência |
|---|---|---|---|
| POPROJ-57 | GitHub e Docker | ✅ | `Dockerfile`, `compose.yaml`, `.github/workflows/`, commit `ac5b5fa` |
| POPROJ-58 | C4 e ADRs | ✅ | 34 ADRs em `ai/adr/`; C4 em `docs/reference/` |
| POPROJ-51 | Multi-tenancy | ✅ | commit `d28e93a`; `@FilterDef`/`@Filter` + filtro explícito |
| POPROJ-13 | Cadastro de Tenant | ✅ | `RegisterTenant`, `TenantController`, SPI-52/53/54 |
| POPROJ-14 | Cadastro de Usuários | ✅ | `CreateUser`, `InviteController`, fluxo de convite |
| POPROJ-16 | Papéis/Hierarquia | ✅ | commit `f8bb2bf`; `POST /api/v1/usuarios/{id}/nivel` |
| POPROJ-15 | Autenticação JWT | ✅ | commit `3f59de8`; `JwtTokenIssuer`, `TenantContextFilter` |

### 3.2 Entregáveis acadêmicos — 9 tasks (⛔ 8 pendentes + 1 em progresso)

Status conforme consolidado (sem evidência em código — são artefatos de disciplina):

| Task | Disciplina | Situação |
|---|---|---|
| POPROJ-60 | Marketing Estratégico | ⛔ To-Do |
| POPROJ-63 | Estatística Multivariada | 🟠 In Progress |
| POPROJ-64 | Modelagem e Construção de Software | ⛔ To-Do |
| POPROJ-65 | Qualidade de Software | ⛔ To-Do |
| POPROJ-66 | Machine Learning | ⛔ To-Do |
| POPROJ-67 | Programação Backend | ⛔ To-Do |
| POPROJ-68 | Pesquisa Operacional | ⛔ To-Do |
| POPROJ-69 | Projeto Integrador IV | ⛔ To-Do |
| POPROJ-71 | Análise Preditiva | ⛔ To-Do |

---

## 4. Sprint 2 — tasks concluídas (✅ 58)

### POPROJ-51 — Multi-tenancy & Isolamento Lógico (2/2)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-15 | Middleware de tenant do contexto autenticado | `TenantContextFilter`; commit `d28e93a` |
| SPI-16 | Filtro obrigatório de `tenant_id` nas leituras | `@Filter` + queries tenant-scoped |

### POPROJ-15 — Autenticação, Sessões e Segurança (5/6)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-26 | Middleware de token nas rotas protegidas | `TenantContextFilter` |
| SPI-28 | Erros de credenciais inválidas / conta bloqueada | `AuthExceptionHandler`, `InvalidCredentialsException` |
| SPI-66 | Expiração/renovação de sessão/token | commit `4e21aac`; `TokenExpirationIT` |
| SPI-67 | Rate limiting / bloqueio após tentativas inválidas | commits `2848257`, `7a2cc2a`, `feeff64`; `LoginRateLimitIT` |
| SPI-68 | Testes de segurança do fluxo de autenticação | `ApiSecurityIT`, `AuthenticationIT` |

### POPROJ-14 — Gestão de Usuários e Convites (8/8)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-29 | Convite gera token/link | commit `793ddaf`; `CreateInvite` |
| SPI-30 | Aceite do convite / definição de senha | `AcceptInvite` |
| SPI-59 | Reenvio de convite expirado / listagem | `ResendInvite`; commit `9b195a1` |
| SPI-60 | Validação de e-mail duplicado no tenant | `UserEmailAlreadyExistsException` |
| SPI-61 | Desativação e reativação de usuário | `ChangeUserStatus` |
| SPI-62 | Listagem/busca por papel/departamento | `ListUsers`; commit `391ce5d` |
| SPI-63 | Edição de papel/departamento | `UpdateUser`; `PATCH /api/v1/usuarios/{id}` |
| SPI-64 | Testes de integração convite→aceite→papel | `InviteFlowIT` |

### POPROJ-16 — Atribuição de Papéis (1/1)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-35 | Endpoint de alteração de nível hierárquico autorizado | commit `21f70e1`; `@RequiresPermission(USER:MANAGE)` |

### POPROJ-52 — RBAC Completo & Matriz de Permissões (11/13)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-39 | Modelo de dados de permissões | commits `ae7d2b5`, `e23cfdc`, `9f661d6`; V005; ADR-33/34 |
| SPI-40 | Policy evaluator | commit `fd36b25`; `PolicyEvaluator` |
| SPI-41 | Middleware de checagem de permissão | commit `b3b6eb0`; `PermissionInterceptor` |
| SPI-42 | Matriz de permissões padrão (nível × área) | commit `1ed5562`; `PermissionMatrix` |
| SPI-43 | Seed inicial da matriz | commit `171d84f`; `ProvisionDefaultRoles` |
| SPI-44 | Endpoint de permissões efetivas | commit `3c9693e`; `GET /usuarios/{id}/permissoes` |
| SPI-45 | RBAC nos endpoints de tenant (POPROJ-13) | `TenantController` `@RequiresPermission(TENANT:MANAGE)` |
| SPI-46 | RBAC nos endpoints de convite/cadastro | `InviteController` `@RequiresPermission(INVITE:MANAGE)` |
| SPI-47 | RBAC nos endpoints de papéis | commit `21f70e1` |
| SPI-49 | Testes de autorização — casos positivos | `AuthorizationIT` |
| SPI-50 | Testes de autorização — casos negativos | `AuthorizationIT` |

### POPROJ-13 — Cadastro de Tenant, Identidade Visual & Onboarding (5/7)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-52 | Edição de dados cadastrais do tenant | commit `d20ba62`; `GET/PUT /api/v1/tenants/me` |
| SPI-53 | Identidade visual (white-label) | commit `608f31d`; V008; `GET/PUT .../identidade-visual` |
| SPI-54 | Domínio de e-mail corporativo único | commits `501f523`, `9d9dc0d`; V007 |
| SPI-55 | Fluxo de onboarding pós-criação do tenant | wizard EJS (`onboarding.ejs`, `step-*`) |
| SPI-58 | Testes de integração do cadastro completo | `TenantManagementIT`, `TenantPersistenceIT` |

### POPROJ-23 — Pipeline de Métricas, Logs de Uso e Custos (10/10)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-69 | Schema `ai_usage_log` | commit `b312437`; V013 |
| SPI-70 | Middleware de captura de métricas no Gateway | `AIRequestCompletedListener` |
| SPI-71 | Endpoint de persistência do log de uso | `UsageRecordController` |
| SPI-72 | Cálculo de custo estimado por requisição | commit `dd4fa2e`; `ModelCostTest` |
| SPI-73 | Consulta de logs por tenant/depto/usuário | `UsageRecordController` |
| SPI-74 | Tratamento de falhas na captura | commit `b312437` |
| SPI-75 | Carga do dataset sintético da QSOFT | commit `a315e82`; V014 |
| SPI-76 | Índices para consultas performáticas | V013 (índices `tenant_created`, etc.) |
| SPI-77 | Documentação do schema de usage log | `docs/development/OBSERVABILITY_USAGE_SCHEMA.md` |
| SPI-78 | Testes de integração do pipeline | `UsageLoggingIT`, `HistoricalUsageIT` |

### POPROJ-21 — Roteamento Determinístico e Modelos de IA (8/8)

| Task | Descrição | Evidência |
|---|---|---|
| SPI-79 | Modelagem do schema de Model | commit `c517366`; V010 |
| SPI-80 | Schema de permissão modelo × departamento | V011 |
| SPI-81 | Cadastro/ativação/desativação de modelo | `AIGatewayCatalogController` |
| SPI-82 | Configuração de departamentos por modelo | `AIGatewayModelPolicyController` |
| SPI-83 | Roteamento determinístico no Gateway | `ResolveModelForUser` |
| SPI-84 | Consulta do modelo permitido ao usuário | `AIGatewayCatalogController` |
| SPI-85 | Erro quando departamento não tem modelo | `NoModelConfiguredException` |
| SPI-86 | Testes do roteamento determinístico | `AIGatewayRoutingIT`, `ResolveModelForUserTest` |

### POPROJ-53 — Dashboard (parcialmente concluídas)

| Task | Descrição | Status | Evidência |
|---|---|---|---|
| SPI-88 | API agregação requisições/usuários ativos | ✅ | commit `246936c`; `DashboardController` |
| SPI-89 | API agregação tokens/custo por departamento | ✅ | commit `246936c` |
| SPI-90 | Filtro de período | ✅ | `DashboardPeriod`; `DashboardMetricsIT` |
| SPI-91 | Filtro de departamento | ✅ | `DashboardQueryCommand` |
| SPI-96 | Testes de integração dos filtros combinados | ✅ | `DashboardMetricsIT` |

> SPI-92, SPI-93, SPI-94 (frontend) e SPI-95 estão detalhadas na seção 5.

---

## 5. Tasks com cobertura parcial (🟠)

### 5.1 SPI-57 — Status do tenant (ativo/suspenso)

- **Grupo:** POPROJ-13
- **Arquivos:** `TenantStatus`, `TenantContextFilter`, `AuthenticateUser`,
  `TenantManagementIT`, `ai/decisions.md` (decisões abertas)
- **Evidência:** commit `71cd779`

**O que se esperava ser desenvolvido**
- `ACTIVE` permite login.
- `INACTIVE` bloqueia login **e** requests protegidos.
- Não criar `SUSPENDED` sem novo ADR.

**O que foi desenvolvido**
- `TenantStatus` com `ACTIVE`/`INACTIVE`.
- Bloqueio de login em `AuthenticateUser` e de requests protegidos em
  `TenantContextFilter` (mesmo com token válido).
- Cobertura de integração em `TenantManagementIT`.

**O que resta para concluir integralmente**
- **Não há endpoint para alterar o status do tenant.** A task pressupõe um
  "admin de plataforma", ator que **não existe** no código — a baseline só
  formaliza atores tenant-scoped (ADR-03/ADR-04/ADR-30), e suspender a partir
  do próprio tenant produz lockout irreversível.
- Depende de **ADR novo** (escopo de plataforma) + emenda ao ADR-03/ADR-30,
  atualização de `ai/architecture.md` (ownership `platform`) e migration de
  auditoria (`status_changed_at`/`status_changed_by`).
- Enquanto não decidido, nenhum endpoint de mudança de status é exposto.

---

### 5.2 SPI-94 — Aplicação do RBAC no dashboard

- **Grupo:** POPROJ-53
- **Arquivos:** `DashboardScopeResolver`, `DashboardController`,
  `DashboardMetricsIT`, `dashboard.js` (não commitado)
- **Evidência:** commit `246936c`

**O que se esperava ser desenvolvido**
- Collaborator: vê apenas o próprio uso.
- Leader/Manager: vê apenas o próprio departamento (mesmo pedindo outro).
- Executive/Admin: vê o tenant conforme permissões.
- Frontend não substitui o enforcement do backend.

**O que foi desenvolvido**
- `DashboardScopeResolver` deriva `AuthorizationScope` do IAM e força:
  `SELF` → próprio usuário; `DEPARTMENT` → departamento do usuário (ignora o
  pedido); `TENANT` → tenant, com departamento opcional.
- `DashboardController` exige `USAGE:READ` e aplica o escopo no backend.
- `DashboardMetricsIT` prova: manager pedindo outro departamento recebe o seu;
  collaborator vê apenas 1 requisição/1 usuário; sem token → 401.

**O que resta para concluir integralmente**
- **Commit** do frontend do dashboard (hoje em working tree, não versionado).
- Reflexo do escopo na UI: ocultar/desabilitar o filtro de departamento para
  escopos `SELF`/`DEPARTMENT` (hoje o filtro é sempre exibido).

---

### 5.3 SPI-92 — Layout e componentes visuais do dashboard

- **Grupo:** POPROJ-53
- **Arquivos (não commitados):** `modules/dashboard/views/index.ejs`,
  `routes/dashboard.routes.js`, `app.js` (registro da rota)
- **Evidência:** `git status` — arquivos untracked/modificados

**O que se esperava ser desenvolvido**
- Interface responsiva aderente à identidade visual do tenant.

**O que foi desenvolvido**
- View `dashboard/views/index.ejs` com cards de KPI (requisições, usuários
  ativos, custo), filtros de período/departamento, área de série temporal e
  tabela de custos por departamento.
- Rota Express `GET /dashboard` e registro em `app.js`.
- Link de acesso ao dashboard em `onboarding.ejs`.

**O que resta para concluir integralmente**
- **Versionar (commit)** os arquivos do dashboard.
- Validar/ajustar **responsividade** e aderência à **identidade visual do tenant
  (white-label)** — não há CSS dedicado nem uso da branding do tenant na view.
- Não há teste de render/UI associado.

---

### 5.4 SPI-93 — Integração do frontend com as APIs de agregação

- **Grupo:** POPROJ-53
- **Arquivos (não commitados):** `public/js/pages/dashboard.js`
- **Evidência:** `git status`

**O que se esperava ser desenvolvido**
- Atualização assíncrona e feedback de carregamento na alteração de filtros.

**O que foi desenvolvido**
- `dashboard.js` consome `GET /api/v1/observability/metrics/usage` e
  `/costs` com `Promise.all`, monta `queryString` com `from`/`to`/`departmentId`
  e re-renderiza KPIs, série e tabela.
- Carrega a lista de departamentos para o filtro.

**O que resta para concluir integralmente**
- **Versionar (commit)** o arquivo.
- **Feedback de carregamento explícito** (spinner/skeleton/estado "carregando")
  durante a requisição — hoje só há mensagem após sucesso/erro.
- Revisar tratamento de respostas de erro por status (401/403) na UI.

---

### 5.5 SPI-95 — Estado vazio e tratamento de erro do dashboard

- **Grupo:** POPROJ-53
- **Arquivos (não commitados):** `public/js/pages/dashboard.js`,
  `modules/dashboard/views/index.ejs`
- **Evidência:** `git status`

**O que se esperava ser desenvolvido**
- Mensagens informativas em períodos sem dados ou indisponibilidade da API.

**O que foi desenvolvido**
- `renderSeries` e `renderCustos` exibem mensagem de "sem dados no período".
- `catch` em `carregar()` limpa os valores e exibe alerta de aviso com a
  mensagem do erro.
- Validação de período invertido (`De > Até`) na UI.

**O que resta para concluir integralmente**
- **Versionar (commit)** os arquivos.
- Cobrir explicitamente **401/403/timeout** com mensagens adequadas.
- **Testes** (integração/e2e) dos estados vazio e de erro.

---

## 6. Tasks pendentes (⛔)

### 6.1 SPI-48 — Tela de configuração de permissões (admin)

- **Grupo:** POPROJ-52 · **Status packet:** Dev (defasado)
- **O que se esperava:** interface web para administradores visualizarem e
  configurarem matrizes de permissão.
- **Situação atual:** **não há tela dedicada**. O módulo admin possui apenas
  `convites.ejs` e `step-admin.ejs`. Não há endpoint de edição de matriz/papéis
  exposto no backend (`RoleAssignment` é escrito apenas pelo `DemoDataSeeder` e
  lido pelo `PolicyEvaluator`).
- **O que resta:** definir/implementar endpoints de gestão de papéis e
  atribuições + tela administrativa; exige decisão sobre o modelo de edição
  (ADR-33).

### 6.2 SPI-51 — Documentação da matriz de permissões

- **Grupo:** POPROJ-52
- **O que se esperava:** documentação técnica detalhando papéis, recursos e
  regras de avaliação.
- **Situação atual:** não existe documento dedicado. Há apenas a menção parcial
  em `docs/development/CONTRATO_API_FRONT_BACK.md` e o código
  (`PermissionMatrix`, `PolicyEvaluator`, ADR-33).
- **O que resta:** produzir a documentação da matriz (papéis × recursos × ações
  e regras de avaliação).

### 6.3 SPI-56 — Auditoria de criação/alteração do tenant

- **Grupo:** POPROJ-13
- **O que se esperava:** trilha de auditoria (ator, timestamp, campos alterados),
  sem registrar senha/JWT/segredos, tenant-aware.
- **Situação atual:** **nenhum código de auditoria** encontrado em
  `src/main/java` (busca por `audit`/`auditoria` sem resultado).
- **O que resta:** definir e implementar o mecanismo de auditoria + migration;
  requer decisão sobre persistência da trilha (tabela dedicada).

### 6.4 SPI-65 — Fluxo de recuperação de senha (⛔🔒 bloqueada por decisão)

- **Grupo:** POPROJ-15
- **O que se esperava:** link de recuperação com expiração, nova senha seguindo
  política de segurança e link de uso único.
- **Situação atual:** **não implementável sem duas decisões**:
  1. **Canal de entrega do link** — não há infraestrutura de e-mail/notificação
     (nenhuma dependência de mail no `pom.xml`). Retornar o token na resposta
     permitiria sequestro de conta; logá-lo viola o AGENTS §9.
  2. **Política de senha** — não existe validação de força/complexidade no projeto.
- **Situação no frontend:** existe a tela `redefinir-senha.ejs` e
  `public/js/pages/redefinir-senha.js`, mas **sem endpoint no backend**.
- **O que resta:** decidir entre (a) porta `PasswordResetNotifier` com no-op no
  MVP; (b) `spring-boot-starter-mail` (nova dependência → `ai/dependencies.md`/ADR);
  (c) manter fora do MVP. Depois, implementar endpoint + token de uso único +
  política de senha.

---

## 7. Decisões abertas que bloqueiam tasks

Registradas em `ai/decisions.md`:

| Decisão | Task afetada | Situação |
|---|---|---|
| Escopo de plataforma / ator para alterar status do tenant | SPI-57 | Em aberto — exige ADR |
| Recuperação de senha (canal de entrega + política de senha) | SPI-65 | Em aberto — 3 opções |
| Backend compartilhado para rate limiting (ex.: Redis) | SPI-67 | Futuro (hoje em memória, documentado) |
| `hierarchyLevel` × RBAC real (rótulo vs. permissão) | — | Em aberto (achado de review) |

---

## 8. Observações e ressalvas

1. **Defasagem dos task packets:** o campo `Status` dos packets não reflete a
   implementação. Recomenda-se sincronizar ou marcar explicitamente como
   "snapshot do consolidado".
2. **Trabalho não commitado:** o frontend do dashboard (SPI-92/93/94/95) está no
   working tree (`modules/dashboard/`, `public/js/pages/dashboard.js`,
   `routes/dashboard.routes.js`, ajustes em `app.js` e `onboarding.ejs`) e não
   foi versionado.
3. **Gap funcional identificado (fora do escopo deste relatório, mas relevante):**
   `AcceptInvite` cria usuário via `ProvisionUser` com conjunto de permissões
   vazio e **não** cria `RoleAssignment`; como o `PolicyEvaluator` lê
   `iam_role_assignments`, usuários convidados podem não obter permissões
   efetivas. Vale confirmar com um teste.
4. **Este relatório não consultou o Jira.** Os status são inferidos de
   evidência local; divergências com o Jira devem ser reconciliadas.
