# Verificação Fase 0 — Plano de correção N01–N05 × repositório

- **Data:** 2026-10-18
- **Branch / commit:** `b-ihago` @ `9e8333c` (merge `IhanDevelopment`), working tree limpa.
- **Relação com a baseline do plano (`057fbea`, 2026-09-29):** HEAD está 8 commits à frente
  (`fe4ed09`…`9e8333c`). O diff de produção é **trivial**: só 2 linhas
  (`applyTenantFilter` passou de `private` para package-private em
  `ModelRepositoryAdapter` e `UsageRecordRepositoryAdapter`). O resto são ~10.900 linhas de
  **testes unitários** novos (`git diff --stat 057fbea HEAD -- src/main` → 2 arquivos).
  Todos os commits citados no briefing (`ce927f0`, `501f523`, `c517366`, `7e20187`) são
  ancestrais de `057fbea`. Conclusão: as observações sobre código de produção valem
  igualmente para `057fbea` e HEAD.
- **Método:** somente leitura. Nenhum arquivo de produção ou teste foi alterado. Único
  efeito colateral: o Docker Desktop foi iniciado localmente para rodar os ITs.
- **Convenção:** ✅ = verificado no código/teste; ⚠️ = verificado, com ressalva; ❓ = **não
  verificado** (motivo indicado).

---

## 1A. Respostas V01–V21

| # | Resposta | Evidência | Impacto no plano |
|---|---|---|---|
| **V01** ✅ | **`POST /tenants` NÃO cria usuário algum.** Cria o tenant e os 4 papéis padrão (COLLABORATOR/LEADER/MANAGER/EXECUTIVE) e só. O endpoint é público (`permitAll`). Também **não é `@Transactional`**: falha em `provision` deixa tenant sem papéis. Consequência: com `demo-data=false`, um tenant recém-criado **não tem nenhum usuário, nenhuma senha e ninguém pode logar** — portanto ninguém pode convidar. O único caminho que cria usuário autorizado é o `DemoDataSeeder` (desligado por padrão: `application.yml:53`). | `iam/application/RegisterTenant.java:35-64` (sem criação de usuário; `provisionDefaultRoles.provision` na :61; sem `@Transactional`); `TenantController.create`; `SecurityBaselineConfig.java:29-36` | **Confirma e agrava** N01: não é "usuário sem papel", é "tenant real inutilizável". O bootstrap é **funcionalidade nova**, não ajuste. A transação precisa ser adicionada ao `RegisterTenant`. |
| **V02** ✅ | **Não.** V005 só cria `iam_roles`, `iam_role_permissions`, `iam_role_assignments`; nenhum `INSERT`/backfill a partir de `user_permission`. | `db/migration/V005__create_iam_rbac_model.sql` (somente DDL) | Confirma: não há concessões "escondidas" vindas de migração. |
| **V03** ❓/⚠️ | **Não verificado em banco.** Há PostgreSQL escutando em `localhost:5432`, mas `DB_PASSWORD` não tem default nem `.env` no repo; não tentei credenciais. **Análise estática dos únicos escritores**: `user_permission` só é populada por `ProvisionUser` (chamado pelo `DemoDataSeeder` com `EnumSet.allOf` e pelo `AcceptInvite` com `Set.of()`); `iam_role_assignments` só é escrita pelo `DemoDataSeeder` (EXECUTIVE, `department_id NULL`). Logo, em qualquer base populada só pela aplicação: legado completo = só o admin demo; legado parcial = 0; atribuições = só o admin demo; LEADER/MANAGER sem depto = 0. Convites pendentes: desconhecido. | `ProvisionUser.java:60`; `DemoDataSeeder.java:102,128`; `AcceptInvite.java:51`; `grep RoleAssignment.create src/main` | Os abortos da V015 só disparam com dados inseridos à mão. **Antes da V015**, alguém com acesso precisa rodar as contagens no ambiente persistente (consulta em 1D-D20). |
| **V04** ✅ | **Sim, lê do banco a cada requisição**, sem cache (`grep @Cacheable/EnableCaching` vazio). `PermissionInterceptor` → `PolicyEvaluator.evaluate` → `roleAssignmentRepository.findByTenantIdAndUserId` + `roleRepository.findById` por atribuição. Ressalva: `validate()` roda 2× por avaliação e há N+1 por papel — barato no volume atual. | `PermissionInterceptor.java:56-57`; `PolicyEvaluator.java:103-108` | Confirma AZ11 viável: revogação vale na requisição seguinte assim que existir revogação. |
| **V05** ✅ | Claims: `sub` (UserId), `tenant_id`, `iat`, `exp`. **Nada de permissões, nível ou departamento.** | `JwtTokenIssuer.java:34-38` | **Refuta a necessidade da emenda ao ADR-30** (era condicional). Orçamento de emendas cai de 6 para 5. |
| **V06** ✅ | `/usuarios/me/permissoes` e `/usuarios/{id}/permissoes` já usam a **trilha 2** (`PolicyEvaluator.effectivePermissions`, `Resource:Action`). Nunca retornam `MANAGE_*`. Ressalva: ambos avaliam com `departmentId = null`, logo ignoram atribuições departamentais (mesmo efeito de N04). `/{id}/permissoes` exige só `USER:READ` e não verifica escopo do alvo. | `UserController.java:144-160` | Ajusta: SPI-44 já cumpre "nunca `MANAGE_*`". O trabalho restante é retornar **concessões com escopo**. |
| **V07** ✅ | `iam.api.model.Permission` só é importado **dentro do `iam`**: `AuthorizationQuery`, `ProvisionUserCommand`, `RegisterUser`, `User`, `DemoDataSeeder`, `UserJpaEntity`, `AuthorizationQueryAdapter` (+6 testes). Nenhum módulo externo. | `grep -rl 'iam.api.model.Permission;' src/main src/test` | Confirma: remoção localizada no `iam`. Observação: `iam.domain.user.User` importa `iam.api` (inversão de camada interna). |
| **V08** ⚠️ | **`MANAGE` NÃO implica outras ações.** `Role.hasPermission` é `contains` exato; a matriz concede `TENANT:READ` e `TENANT:MANAGE` separadamente. Efeito colateral: os GETs do catálogo exigem `AI_TOOL:MANAGE` (não `READ`). | `Role.java:69`; `PermissionMatrix.java` (executive); `AIGatewayCatalogController.java:55-56,73-74`; comentário em `sharedkernel/rbac/Action.java` ("MANAGE representa administração ampla") **não é implementado** | **Diverge** do ADR-36 item 7: introduzir "MANAGE implica as demais" **amplia** acesso hoje. Ver 1D-D8. |
| **V09** ✅ | **Nenhum caller de produção.** `grep CompleteAIRequest src/main` só encontra a própria classe. Além disso, `CompleteAIRequest` **não chama provider nenhum**: recebe tokens/latência prontos no comando e só calcula custo e publica o evento. Aceita `requestId` vindo do comando. | `aigateway/application/CompleteAIRequest.java:50,78`; ausência de adaptador em `aigateway/infrastructure` | **Confirma N05 latente.** Ajusta a fase 3: não existe "chamada externa" para envolver; o desenho de tentativas nasce junto com o adaptador. `requestId` deve passar a ser só do servidor. |
| **V10** ✅ | Token = `UUID` sem hífens (32 hex, ~122 bits), **texto puro** em `iam_invites.token VARCHAR(64) UNIQUE`; TTL 48 h (`app.security.invite.ttl-hours`); uso único (`Invite.accept` rejeita reuso/expirado); devolvido no `POST /convites` e no `reenviar` (já é, na prática, "link copiável"), **não** devolvido na listagem; nenhum `log.*` no `iam` fora do seeder. O aceite usa `invite.email()` — o aceitante não informa e-mail. | `CreateInvite.java:63,70`; `V006`; `Invite.java:108`; `InviteResponse.java:24`; `AcceptInviteRequest` (token, name, password); `AcceptInvite.java:46` | Ajusta: falta só **hash**. "E-mail igual ao do convite" já vale por construção. |
| **V11** ✅ | Allowlist: `iam → sharedkernel` **apenas**; `department → sharedkernel, iam`. ArchUnit tem `firstLevelPackagesMustBeFreeOfCycles`. | `ai/architecture.md` (Allowlist síncrona); `ArchitectureRulesTest.java:71,117` | **Refuta** "validar `department_id` via `OrganizationQuery`" no `iam`: criaria ciclo `iam↔department` e quebra o ArchUnit. Ver 1D-D6. |
| **V12** ✅ | ADR-04: "IAM é dono de … HierarchyLevel, Permissions e AuthorizationScope. **ADMIN é conjunto de permissões**, não nível hierárquico." ADR-33: papéis por tenant como única fonte; "escopo opcional de Department **avaliado em tempo de execução (não persistido na tupla)**" — texto ambíguo, pois `department_id` é persistido na atribuição. | `ai/adr/ADR-04-iam.md`; `ai/adr/ADR-33-authorization-model-roles.md` | Confirma a necessidade das emendas 04 e 33. ADR-33 já proíbe "matriz global paralela" — e `user_permission`/`resolveScope` são exatamente isso. |
| **V13** ✅ | `iam_roles(id, tenant_id, name, created_at, updated_at)`, `UNIQUE(tenant_id,name)`; `iam_role_permissions(role_id, resource, action)`; `iam_role_assignments(id, tenant_id, user_id, role_id, department_id)` — **sem timestamps**, FK simples para `iam_roles(id)`; tabela de usuário é **`app_user`**; `user_permission(user_id, permission)` **sem `tenant_id`**; `iam_invites(…, hierarchy_level, department_id, token, status, created_at, expires_at, accepted_at)`. | `V002`, `V004`, `V005`, `V006` | Ajusta SQL da V015 (ver 1D-D7). O papel é identificado por **nome = `HierarchyLevel.name()`**. |
| **V14** ✅ | **Sim, o usuário pode alterar o próprio `departmentId`** se tiver `USER:MANAGE` (MANAGER/EXECUTIVE): `UpdateUser` só checa mesmo tenant, não checa ator ≠ alvo nem existência do depto. O mesmo vale para `/nivel` (autopromoção). **O dashboard lê o departamento do banco**, via `IdentityQuery.getUserContext` (não do JWT); a política de modelos (`/models/permitido`) também. | `UpdateUser.java:48-53`; `ChangeUserHierarchyLevel.java:50`; `DashboardScopeResolver.java:48-61`; `ResolveModelForUser.java:55` | **Confirma o segundo vetor.** E há um **terceiro** que o plano não viu: depto nulo → visibilidade de tenant (1D-D4). |
| **V15** ✅ | Sim. `TenantContextFilter` revalida tenant ACTIVE + user ACTIVE + mesmo tenant em toda requisição autenticada (401 caso contrário); `PolicyEvaluator.validate` repete a checagem. | `TenantContextFilter.java:56`; `PolicyEvaluator.validate` | Confirma: desativação vale na hora. |
| **V16** ⚠️ | **Nem `ON CONFLICT` nem captura de unicidade**: `RecordUsage` faz *check-then-insert* (`existsByRequestId`). Há `UNIQUE(request_id)` na V013; numa corrida a segunda inserção estoura `DataIntegrityViolation`, que o listener engole. | `RecordUsage.java:31`; `V013` (`uq_observability_usage_records_request`) | Confirma o item da fase 3. |
| **V17** ✅ | O front **não usa `MANAGE_*` nem `hierarchyLevel` para menus**: usa um papel **simulado** em `localStorage('simulatedUserRole')`, default `'admin'`. Não chama `/me/permissoes`. A edição de nível chama `POST /usuarios/{id}/nivel` com *fallback* simulado. | `public/js/pages/admin-usuarios.js:5,98,201,296-300`; `admin-convites.js:5,73` | Ajusta: o `grep MANAGE_` no front já dá zero. O trabalho real é **trocar o papel simulado por `/me/permissoes`** (UI apenas; backend continua decidindo). |
| **V18** ✅ | Unit + ArchUnit: **717 testes, 0 falhas** (`mvn -B test`). Integração: ver **1B**. | log local de `mvn -B test` | A linha de base "126 + 52" está desatualizada. |
| **V19** ✅ | **10 ITs** inserem `RoleAssignment` direto: `ApiSecurityIT:124`, `AuthorizationIT:123`, `InviteFlowIT:111`, `TenantManagementIT:121`, `UserManagementIT:118`, `AIGatewayRoutingIT:117`, `DashboardMetricsIT:155`, `DepartmentManagementIT:118`, `UsageLoggingIT:134`, `RolePersistenceIT:112` (este é teste de persistência, legítimo). Todos com `department_id = null`. `InviteFlowIT.shouldCreateInviteAcceptAndLogin` só testa login, **não** que o convidado consegue acessar algo — por isso N01 passou. | `grep -rn 'RoleAssignment.create' src/test --include=*IT.java` | Confirma N07. 9 fixtures a trocar por helper de produção. |
| **V20** ✅ | Excluir depto = **soft delete** (`deactivate`), sem checar usuários, atribuições ou política de modelos. Usuários continuam com `departmentId` apontando para depto INACTIVE. O adaptador da política de modelos não filtra por status (grep sem `status`). | `DeleteDepartment.java:33`; `DepartmentModelPolicyQueryAdapter.java` | Confirma o risco residual 6; acrescenta: depto inativo continua resolvendo política de modelos (❓ não testado em IT). |
| **V21** ✅ | Convenção **de fato**: recurso de **outro tenant → 404** (repositórios tenant-scoped lançam `*NotFound`; ex.: `UserApiExceptionHandler` 404, `ModelNotFound` 404); **sem permissão no gate → 403** (interceptor); convite de outro tenant → **400** (`InvalidInviteException`). Não há regra escrita. Obs.: `IllegalStateException → 401` em 3 handlers. | `UserApiExceptionHandler.java:25-38`; `InviteApiExceptionHandler.java:19-21`; `AIGatewayApiExceptionHandler.java:26-29` | Recomendação: formalizar "outro tenant = 404; mesmo tenant fora de escopo = 403" (decisão 9). |

---

## 1B. Reexecução da suíte

| Suíte | Comando | Resultado | Relatado antes |
|---|---|---|---|
| Unit + ArchUnit | `mvn -B test` | **717 run, 0 falhas, 0 erros, 0 skipped** — BUILD SUCCESS. `ArchitectureRulesTest`: 4/4. | 126 |
| Integração (Testcontainers / PostgreSQL) | `mvn -B verify -Pintegration` | **83 run, 0 falhas, 0 erros, 0 skipped** em 17 classes `*IT` — BUILD SUCCESS (Docker 29.8.1). | 52 |

Notas:
- **Total real: 717 + 83 = 800 testes verdes** (vs. 126 + 52 relatados). O aumento é quase todo de testes unitários adicionados depois de `057fbea`.
- Java local é **25.0.2** (o `pom.xml` declara `<java.version>21</java.version>`). ❓ Não rodei com JDK 21; o DoD pede Java 21.
- Suíte verde **não** significa N01 coberto: nenhum IT exercita "registrar tenant → convidar → aceitar → acessar" com `demo-data=false` — esse é o primeiro teste negativo a escrever (texto pronto em `ai/tasks/sprint-03/HANDOFF.md` §5) (hoje falharia já no passo "convidar": não há quem convide).
- **ITs que inserem `RoleAssignment` manualmente (V19):** ver tabela acima (10 arquivos, 9 a corrigir).

---

## 1C. Reconciliação Sprint 3 × código (57 tarefas)

Legenda do estado real: **Impl.** = implementado; **Parcial**; **Ausente**.

### POPROJ-52 — RBAC

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-42 | Dev | Impl. (modelo antigo: matriz por nível, herança COLLAB⊆…⊆EXEC, EXECUTIVE = admin) | `iam/domain/rbac/PermissionMatrix.java` | **Redefinir** (matriz de papéis de sistema c/ escopo + `TENANT_ADMIN`). |
| SPI-43 | A fazer | Impl. (seed por tenant, idempotente, chamado no registro) | `ProvisionDefaultRoles.java`; `RegisterTenant.java:61` | **Redefinir** como V015 (status no Jira está atrasado). |
| SPI-44 | Dev | Impl. (trilha 2; nunca `MANAGE_*`); ignora escopo departamental | `UserController.java:144-160` | **Manter** com escopo novo: retornar concessões efetivas c/ escopo. |
| SPI-46 | A fazer | Parcial: gate `INVITE:MANAGE` existe; convite **não** carrega concessão; qualquer MANAGER convida qualquer nível | `InviteController.java:64-111`; `CreateInvite` | **Redefinir** (fase 2). |
| SPI-47 | A fazer | Parcial: não há endpoint de papéis; `/nivel` tem gate `USER:MANAGE` | `UserController.java:130-141` | **Redefinir** (fase 2: endpoints de concessão). |
| SPI-50 | Concluído | Impl., mas fixtures manuais; 1 caso negativo em `AuthorizationIT` + `ApiSecurityIT` | `AuthorizationIT.java:166`; V19 | **Manter fechada**; AZ01–AZ19 entram nas tarefas das fases 1–2. |

### POPROJ-13

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-54 | A fazer | **Impl.** (V007 `email_domain` único; 409 em sobreposição; login por domínio; ITs) | `V007`; `RegisterTenant.java` (check `existsByEmailDomain`); `ai/decisions.md` "RESOLVIDO" | **Fechar.** Nota "verificar" **confirmada**. |

### POPROJ-23 — Logs de consumo

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-69 | Concluído | Impl. | `V013` | Manter fechada. |
| SPI-70 | Concluído | Impl. (listener do evento) | `AIRequestCompletedListener` | Manter. |
| SPI-71 | Concluído | Impl. como evento→`RecordUsage` (não há endpoint de escrita — correto pelo ADR-13) | `RecordUsage.java` | Manter. |
| SPI-72 | Concluído | Impl. (`Model.estimatedCost`, no aigateway) | `CompleteAIRequest.java` | Manter. |
| SPI-73 | A fazer | **Parcial — com falha de autorização**: `GET /api/v1/observability/usage` existe, mas **não aplica escopo algum**; qualquer portador de `USAGE:READ` (inclusive COLLABORATOR) lista todo o uso do tenant e filtra por qualquer `userId`. | `UsageRecordController.java:33-44`; `QueryUsage` (sem escopo) | **Não fechar.** Redefinir: aplicar `VisibilityScope` (fase 1). Nota "verificar" **confirmada quanto à existência, desmentida quanto a estar pronta**. |
| SPI-74 | Concluído | Impl. no sentido "falha não derruba a completion"; **N05 aberto** (listener engole e perde o registro), porém **latente** (sem caller) | `AIRequestCompletedListener.java:33`; V09 | **Manter fechada** e **criar tarefa nova da fase 3** (ADR-38, TR01–TR07), atrelada ao provider. |
| SPI-75 | Concluído | Impl. | `V014` | Manter. |
| SPI-76 | Concluído | Impl. (3 índices `tenant_*_created`) | `V013` | Manter. |
| SPI-77 | A fazer | **Impl.** (documento existe) | `docs/development/OBSERVABILITY_USAGE_SCHEMA.md` | **Fechar.** Nota confirmada. ❓ conteúdo não revisado em detalhe. |
| SPI-78 | A fazer | Impl. para o caminho feliz: `UsageLoggingIT` (persistência via completion, idempotência, isolamento de tenant, 401) | `UsageLoggingIT.java:186-235` | **Fechar**; TR04/TR05 vão para a tarefa da fase 3. |

### POPROJ-21 — Catálogo e roteamento

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-79 | Concluído | Impl. | `V010`, `V012` | Manter. |
| SPI-80 | Concluído | Impl. | `V011` | Manter. |
| SPI-82 | Concluído | Impl. | `AIGatewayModelPolicyController` `PUT /departments/{id}/model-policy` | Manter. |
| SPI-83 | A fazer | **Impl.** | `ResolveModelForUser.java`; `GET /ai-gateway/models/permitido` | **Fechar.** Nota confirmada. |
| SPI-85 | A fazer | **Impl.**: sem política/default → 409; modelo fora da política → 403; inexistente → 404 | `AIGatewayApiExceptionHandler.java:26-51` | **Fechar.** Nota confirmada. |
| SPI-86 | A fazer | **Impl.**: `AIGatewayRoutingIT` (7 testes) + `ResolveModelForUserTest` | `integration/AIGatewayRoutingIT.java` | **Fechar.** Nota confirmada. |

### POPROJ-53 — Dashboard

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-88 | Concluído | Impl. | `DashboardController` `/metrics/usage` | Manter. |
| SPI-89 | Concluído | Impl. | `/metrics/costs` | Manter. |
| SPI-90 | A fazer | **Impl.**: `from/to`, padrão 30 dias, rejeita período invertido; IT | `DashboardPeriod.java:16`; `DashboardMetricsIT.shouldRejectInconsistentPeriod` | **Fechar.** Nota confirmada. |
| SPI-94 | A fazer | Parcial: `DashboardScopeResolver` usa `resolveScope` (rótulo) e **falha aberto** com depto nulo | `DashboardScopeResolver.java:48-61` | **Manter esta**, redefinir p/ `VisibilityScope` (fase 1). |
| SPI-112 | A fazer | = SPI-94 | — | **Duplicata — fechar.** |
| SPI-95 | A fazer | Parcial: front tem `catch`/estado de erro; semântica "ausente ≠ zero / inconclusivo ≠ zero" ausente | `public/js/pages/dashboard.js:71,147` | Manter (pós fase 1). |
| SPI-96 | A fazer | Impl. para período + depto (`shouldFilterCostsByDepartment`, manager forçado ao próprio depto) | `DashboardMetricsIT.java:214` | **Redefinir** p/ DB01–DB03 (403 fora do escopo, união, mudança de depto). |
| SPI-97 | A fazer | Ausente (só série por período e custo por depto) | `UsageMetricsRepository.java:20,26` | Manter uma (97). |
| SPI-113 | A fazer | = SPI-97 | — | **Duplicata — fechar.** |
| SPI-98 | A fazer | Ausente no dashboard | idem | Manter uma (98); depende da fase 1. |
| SPI-114 | A fazer | = SPI-98 | — | **Duplicata — fechar.** |
| SPI-99 | A fazer | Ausente (`modelId` não é filtro) | `DashboardController.java:44-63` | Manter uma (99). |
| SPI-115 | A fazer | = SPI-99 | — | **Duplicata — fechar.** |
| SPI-100 | A fazer | Ausente no dashboard (existe só no endpoint cru, sem escopo) | idem | Manter uma (100); depende da fase 1. |
| SPI-116 | A fazer | = SPI-100 | — | **Duplicata — fechar.** |
| SPI-101 | A fazer | Parcial: índices V013; sem trabalho específico medido | `V013` | Manter uma (101); **mover** para depois (sem gatilho medido). |
| SPI-117 | A fazer | = SPI-101 | — | **Duplicata — fechar.** |

### POPROJ-54 — Orçamento (fase 5)

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-118 | A fazer | Ausente (só valor corrente `monthly_budget`, sem histórico) | `V009` | **Mover** p/ próxima sprint. |
| SPI-119 | A fazer | Parcial: `monthlyBudget` editável no CRUD de departamento | `DepartmentRequest.java:23-24`; `PUT /departamentos/{id}` | Mover; reaproveitar o CRUD. |
| SPI-120 | A fazer | Ausente (`DepartmentBudgetQuery` existe, sem consumidor) | `department/api/query/DepartmentBudgetQuery.java` | Mover. |
| SPI-121 | A fazer | Ausente | — | Mover. |
| SPI-122 | A fazer | Ausente | — | Mover. |
| SPI-123 | A fazer | Ausente | — | Mover. |
| SPI-124 | A fazer | Ausente | — | Mover. |
| SPI-125 | A fazer | Ausente (duplica 118 em parte) | — | Mover; avaliar fusão com 118. |
| SPI-126 | A fazer | Ausente | — | Mover. |

### POPROJ-36 — Agente de Políticas / RAG (bloqueado)

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-127 | A fazer | Ausente; Spring AI = dependência nova | `pom.xml` sem Spring AI; `agentpolicies/*` só `.gitkeep` | **Mover** (bloqueada: ADR-35 inexistente). |
| SPI-128 | A fazer | Ausente; pgvector proibido sem ADR | idem | Mover. |
| SPI-129 | A fazer | Ausente | idem | Mover. |
| SPI-130 | A fazer | Ausente | idem | Mover. |
| SPI-132 | A fazer | Ausente | idem | Mover. |
| SPI-134 | A fazer | Ausente (módulo `chat` vazio) | `chat/*` só `.gitkeep` | Mover p/ fase 4 (separar do RAG). |
| SPI-135 | A fazer | Ausente | — | Mover. |

### POPROJ-19 — Chat

| Chave | Jira | Estado real | Evidência | Ação |
|---|---|---|---|---|
| SPI-136 | A fazer (Lyn) | Ausente no repo (nenhum arquivo de chat no front; backend `chat` vazio) | `grep -ril chat src/main/resources/static` vazio | **Manter com Lyn**; não tocado. Atenção: sem backend de chat, a UI não tem contra o que integrar nesta sprint. |

**Resumo:** fechar por status atrasado — **SPI-54, 77, 78, 83, 85, 86, 90** (7); fechar por duplicata — **112, 113, 114, 115, 116, 117** (6); **SPI-73 não pode ser fechada** (existe, mas sem escopo); **SPI-74** fica fechada + tarefa nova da fase 3.

---

## 1D. Divergências do plano × repositório

| # | O plano diz | O repositório mostra | Correção proposta |
|---|---|---|---|
| D1 | N01 "possivelmente pior". | **Confirmado e pior**: tenant real nasce **sem usuário nenhum** (não só sem papel). Além disso, `POST /usuarios` cria usuário **sem senha** e o convite para o mesmo e-mail é **recusado** (`CreateInvite` → `UserEmailAlreadyExistsException`) — usuário criado por `POST /usuarios` **nunca consegue logar** (não há recuperação de senha). | Bootstrap é feature nova (fase 2) + `@Transactional` no `RegisterTenant`. Reforça **remover `POST /usuarios`** (decisão 3). |
| D2 | N02: único leitor vivo da trilha 1 é `resolveScope`; `checkPermission` só em código morto. | **Confirmado**: `checkPermission` só é chamado por `RegisterUser`, que não tem caller. `resolveScope` só por `DashboardScopeResolver`. | Sem mudança. |
| D3 | N03: autopromoção de rótulo + troca do próprio depto. | **Confirmado** (V14). Detalhe: a autopromoção só afeta o **dashboard** (único leitor de `hierarchyLevel`); gates não leem nível. A troca de depto afeta dashboard **e** política de modelos. `UpdateUser` não valida se o depto existe/está ativo. | Sem mudança de mérito. |
| D4 | — (não visto) | **Vetor novo, *fail-open*:** LEADER/MANAGER com `departmentId = null` cai em `scopedDepartmentId = null` e o SQL não filtra depto → **visibilidade do tenant inteiro**. Usuários convidados/criados sem depto e rotulados LEADER/MANAGER já nascem assim. | `VisibilityScope` sem caminho nulo-permissivo; teste DB04 "gestor sem depto → só SELF/None". Entra na fase 1. |
| D5 | — (não visto) | **Vetor novo:** `GET /api/v1/observability/usage` (SPI-73) **não aplica escopo**. Hoje latente (só o admin demo tem papel); **fica ativo no instante em que N01 for corrigido** (COLLABORATOR tem `USAGE:READ`). | Incluir `UsageRecordController`/`QueryUsage` na fase 1 (mesmo `VisibilityScope` do dashboard) — **bloqueia** o release das fases 1+2. Teste novo (AZ20). |
| D6 | `department_id` da concessão "validado via `OrganizationQuery`". | **Impossível sem violar a allowlist**: `iam → sharedkernel` apenas; `department → iam` já existe; ArchUnit proíbe ciclo. | (a) **Recomendado p/ MVP:** não validar existência no `iam`; concessão para depto inexistente/de outro tenant **não casa com nenhum alvo** (fail-closed), e o front oferece só a lista de deptos. (b) Alternativa: porta SPI em `iam.api` implementada pelo `department` (exige emenda ADR-02/31). |
| D7 | SQL da V015 (`system_key`, `scope_department_id` em convites etc.). | Tabela de usuário é `app_user`; `user_permission` sem `tenant_id` (backfill precisa de `JOIN app_user`); papéis identificados por `name` = nível; `iam_role_assignments` sem timestamps; `iam_invites` **já tem `department_id`** e `hierarchy_level`. | Com escopo **fixo por papel**, `scope_type` é derivável do papel → em convites basta `role_id` + `invited_by` (reusar `department_id`; dispensar `scope_type`/`scope_department_id`). Hash do token: fazer em Java e **revogar** pendentes na V015 (evita depender de `pgcrypto`). |
| D8 | ADR-36 item 7: "`MANAGE` implica as demais ações no mesmo escopo". | Hoje **não implica** (`Role.java:69`). | **Remover o item 7**: manter *match* exato (mais simples e fechado). AZ10 continua válido e passa a ser trivial. |
| D9 | Regra de "catálogo legível" (`MODEL:READ`, `AI_TOOL:READ`, `TENANT:READ`) e teto `CHAT=SELF`. | **Nenhum endpoint usa** `MODEL:*`, `AI_TOOL:READ`, `TENANT:READ`, `ROLE:*`, `CHAT:*`, `KNOWLEDGE_*`, `LICENSE`, `TRAINING`, `GOVERNANCE_SCORE`, `PROJECT`. Em uso: `AI_TOOL:MANAGE`(6), `DEPARTMENT:READ/MANAGE`, `INVITE:MANAGE`, `TENANT:MANAGE`, `USAGE:READ`, `USER:READ/MANAGE`. `/models/permitido` e `GET /tenants/me` são só autenticados. | **Adiar** a regra de catálogo (sem consumidor). Manter o teto `CHAT=SELF` só como regra unitária no avaliador (AZ12 unit). Reduz superfície da fase 1. |
| D10 | N04 *fail-closed*. | **Confirmado** (`PermissionInterceptor.java:57` passa depto nulo; `PolicyEvaluator.java:104` descarta a atribuição departamental). E **nenhum caminho de produção cria atribuição departamental** — N04 é inteiramente teórico hoje. | Sem mudança; severidade de segurança atual = nenhuma. |
| D11 | N05: proteger a "chamada externa" com intenção prévia. | `CompleteAIRequest` **não faz chamada externa** e aceita `requestId` do chamador. | Fase 3 nasce com o adaptador; `requestId` só do servidor. A fase 3 foi mantida na sprint (decisão 15): o código usa um adaptador `STUB` até o provider ser aprovado (TASK-S3-C1). |
| D12 | Emenda ao ADR-30 condicional. | Claims estáveis (V05). | **Retirar** a emenda ao ADR-30. |
| D13 | Remover `MANAGE_*` de JWT, `/me/permissoes` e menus. | Já não existe em nenhum dos três. Front usa papel **simulado** em `localStorage`. | Trocar o item por "front lê `/me/permissoes`" (escopo de UI; ver regra SPI-136). |
| D14 | Fixtures de IT. | 9 ITs a corrigir (V19). Além disso, `DashboardMetricsIT` atribui MANAGER **tenant-wide** e testa o rótulo — mascara N03/N04. | Helper único de fixture pelos casos de uso de produção (fase 1, depois do bootstrap existir → **a fase 1 precisa de parte da fase 2**: ver replanejamento). |
| D15 | — (não visto) | `RoleRepositoryAdapter.findById` não filtra tenant e `PolicyEvaluator` não compara `role.tenantId()` com o contexto. | Além da FK composta, checar tenant do papel no avaliador (barato). |
| D16 | Matriz: TENANT_ADMIN com `AI_TOOL: READ+MANAGE`. | Os GETs do catálogo exigem `AI_TOOL:MANAGE`. | Coerente; manter `AI_TOOL:MANAGE` no TENANT_ADMIN. EXECUTIVE perde acesso ao catálogo — confirmar com o PO. |
| D17 | Linha de base 126 + 52. | 717 unit + ver 1B. | Atualizar a linha de base no plano. |
| D18 | Fora do plano | `scripts/db/fix-crossmodule.sql` altera tabelas `historical_*` e referencia `project_projects`, que **não existe em nenhuma migration Flyway** (SQL manual fora do Flyway). | Só registro; não bloqueia N01–N05. |
| D19 | Fora do plano | `IllegalStateException → 401` em 3 *handlers*: qualquer bug que lance `IllegalStateException` vira "Não autenticado". | Registro; tratar quando tocar nos handlers. |
| D20 | V03 | Não foi possível consultar. | Antes da V015, rodar no ambiente persistente: `SELECT count(*) FROM user_permission GROUP BY user_id HAVING count(*) BETWEEN 1 AND 8;` (parciais), `… HAVING count(*)=9` (admins), `SELECT r.name, a.department_id IS NULL, count(*) FROM iam_role_assignments a JOIN iam_roles r ON r.id=a.role_id GROUP BY 1,2;`, `SELECT status, count(*) FROM iam_invites GROUP BY 1;`. |

---

## Decisões humanas pendentes — o que a Etapa 1 respondeu

| # | Decisão | Fatos da Etapa 1 | Situação | Recomendação e impacto |
|---|---|---|---|---|
| 1 | Bootstrap: registrante vira `TENANT_ADMIN` × comando de operador | Hoje não existe primeiro usuário (V01); `POST /tenants` é público. | **Aberta** | Registrante vira `TENANT_ADMIN` na mesma transação. Impacto: `POST /tenants` passa a receber nome/e-mail/senha do admin (muda o passo `step-tenant` do onboarding); agrava a ocupação de domínio (risco residual 1). Operador: sem auto-cadastro público de admin, mas exige ferramenta de operação e complica a demo. |
| 2 | `TENANT_ADMIN` separado × EXECUTIVE como admin | EXECUTIVE hoje tem todos os `MANAGE` usados (D9, D16). | **Aberta** | Separar. Impacto: EXECUTIVE perde catálogo/deptos/tenant; quem é diretor e admin recebe 2 concessões. |
| 3 | Remover `POST /usuarios` × exigir concessão | Usuário criado por ele nunca consegue logar (D1). | **Respondida por fato** | Remover. Impacto: o fluxo manual de usuários do front passa a usar convite. |
| 4 | Escopo fixo por papel | Nenhum consumidor precisa de escopo variável. | Aberta (fatos a favor) | Sim. Reduz colunas da V015 (D7). |
| 5 | Gestor só convida posto menor no próprio depto | Hoje qualquer `INVITE:MANAGE` convida qualquer nível em qualquer depto. | **Aberta** | Sim (dominância). Impacto: AZ07. |
| 6 | Regras do convite | Link copiável **já existe**; e-mail igual **já vale por construção**; token em texto puro (V10). | **Parcial** | Abertos: hash do token, revalidação no aceite, revogar pendentes na V015 — recomendo os três. |
| 7 | V016 no mesmo release × no seguinte | V03 não verificado em banco. | **Aberta** | Release seguinte: o código deixa de ler na fase 2; o `DROP` fica barato e reversível até lá. |
| 8 | Casos ambíguos da migração | Pela análise estática, zero casos via aplicação (V03). | **Aberta** | Abortar (como no plano) e rodar as contagens de D20 antes. |
| 9 | 403 × 404 | De fato: outro tenant = 404; mesmo tenant sem permissão = 403 (V21). | Respondida (falta formalizar) | Formalizar essa regra no ADR-36. |
| 10 | Filtro do dashboard fora do escopo → 403 | Hoje é ignorado em silêncio (`DashboardScopeResolver:55-61`). | **Aberta** | 403. Impacto: `dashboard.js` deve esconder o filtro e tratar 403. |
| 11 | Provider real e integração | Não há adaptador; `CompleteAIRequest` sem caller (V09). | **Aberta** | `RestClient` do Spring (sem dependência nova). Mantido na Sprint 3 pela decisão 15 (TASK-S3-C1); o provider concreto da demo ainda precisa ser escolhido. |
| 12 | Retentativa e retenção | — | Aberta | Adiar para a fase 3. |
| 13 | Erro do provider = custo zero? | — | Aberta | `INCONCLUSIVE`, a menos que o adaptador declare "sem cobrança". Adiar. |
| 14 | RF047 inclui tentativas negadas? | — | Aberta | Sim, só metadados. Adiar. |
| 15 | Orçamento e cortes | Fase 3 sem provider; RAG sem ADR-35; orçamento depende das fases 1–2. | **Aberta** | Tirar da Sprint 3: RAG (127–135), orçamento (118–126), fase 3. Novo orçamento: 1 migration de autorização nesta sprint (V015), até 2 endpoints novos (revogar concessão + listar papéis); atribuição direta adiada. |

### Decisões tomadas pelo responsável (2026-10-18)

- **1, 2, 5, 6, 7, 10:** seguir as recomendações da tabela acima.
- **3:** remover `POST /usuarios` (respondida pelos fatos — D1).
- **15:** **não** cortar escopo. RAG (SPI-127…135), orçamento (SPI-118…126), chat e tentativas ficam na Sprint 3, porque é preciso ter algo apresentável. Ordem de execução: fases 1+2 (base de segurança) → orçamento → provider/tentativas → chat/RAG.
- **Ainda dependem de aprovação explícita antes de codar** (regras do `AGENTS.md` §19 e do próprio pedido): ADR-35 (RAG), dependências novas (Spring AI, pgvector), o provider real (decisão 11) e estourar os tetos do orçamento de complexidade (migrations, endpoints, ADRs).
- **11, 12, 13, 14:** continuam abertas.
- **Renumeração:** o plano original chamava de V016 o `DROP` de `user_permission` e de V017 a tabela de tentativas. Com o escopo mantido, a numeração passou a ser: V015 autorização, V016 orçamento, V017 provider/tentativas, V018 chat, V019 conhecimento e V021+ o `DROP` (release seguinte). Ver `ai/tasks/sprint-03/INDEX.md` §4.
- **Modo de trabalho (18/10):** a partir deste ponto, este agente só planeja. A implementação fica a cargo de outro modelo, seguindo `ai/tasks/sprint-03/HANDOFF.md`.
