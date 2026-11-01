# Contrato de API — Frontend × Backend

> **Estado atual: 2026-10-20** (Sprint 3, A1–A10 e ondas B/C/D/E/F aplicadas).
> As §1, §2 e §2.7 descrevem o contrato **vigente**. A partir da §3, o conteúdo é o **registro
> histórico** da reconciliação de 27/09 (Opção A).
>
> Referências: `ai/tasks/sprint-03/SPEC-01-autorizacao.md` §6–§7 (autorização) e
> `docs/development/MATRIZ_PERMISSOES.md` (matriz conferida contra o código).

---

## 1. Princípios

1. **Backend é a fonte de verdade.** O contrato REST canônico segue ADR-28: prefixo `/api/v1`, controllers por módulo, `REST → application use case`.
2. **Tenant nunca vem do cliente.** O `tenantId` é derivado do `TenantContext` (JWT); o frontend não envia `tenantId`.
3. **Autorização é do backend.** Endpoints protegidos usam RBAC granular (`@RequiresPermission`) e respondem `401` (sem token) / `403` (sem permissão).
4. **O frontend se adapta** (Opção A): os caminhos/verbos do frontend são ajustados ao contrato canônico; onde o backend ainda não expõe um recurso necessário, o backend o implementa **com a nomenclatura canônica** (prioridade backend).
5. **Um único cliente HTTP no browser** (base `/api/v1`), eliminando a duplicidade atual (`window.API` × `window.ApiClient`).

---

## 2. Contrato vigente do backend

Convenções:
- `/api/v1/{recurso}`;
- paginação `page`/`size` (0-based; padrão 20, máximo 100);
- erros `{ "message": "..." }`;
- sem token → 401; sem permissão no gate → 403; recurso de outro tenant → 404.

O tenant vem sempre do JWT.

### 2.1 Autenticação, tenant e usuários (`iam`)

| Método | Caminho | Corpo | Autorização |
|---|---|---|---|
| POST | `/api/v1/auth/login` | `{ tenantSlug?, email, password }` → `{ token, tokenType }` | público |
| POST | `/api/v1/tenants` | `{ slug, name, emailDomain, adminName, adminEmail, adminPassword }` (cria tenant + admin `TENANT_ADMIN`) | público |
| GET / PUT | `/api/v1/tenants/me` | PUT: `{ name }` | autenticado / `TENANT:MANAGE` |
| GET / PUT | `/api/v1/tenants/me/identidade-visual` | PUT: `{ logoUrl, primaryColor, secondaryColor }` | autenticado / `TENANT:MANAGE` |
| ~~POST~~ | ~~`/api/v1/usuarios`~~ | **removido** (404/405); criação só por convite | — |
| GET | `/api/v1/usuarios` | filtros `hierarchyLevel`, `departmentId`, `name`, `email`, `page`, `size` | `USER:READ` (BT) |
| PATCH | `/api/v1/usuarios/{id}` | `{ departmentId }` | `USER:MANAGE` |
| PATCH | `/api/v1/usuarios/{id}/status` | `{ status }` | `USER:MANAGE` |
| POST | `/api/v1/usuarios/{id}/nivel` | `{ hierarchyLevel }` (rótulo, sem efeito de autorização) | `USER:MANAGE` |
| GET | `/api/v1/usuarios/me/permissoes` | → `{ userId, concessoes, permissoes: ["RESOURCE:ACTION"] }` | autenticado |
| GET | `/api/v1/usuarios/{id}/permissoes` | idem | `USER:READ` (BT) |
| GET | `/api/v1/papeis` | → `[{ key, escopo, posto, concedivel }]` | `ROLE:READ` (BT) |
| POST | `/api/v1/usuarios/{id}/concessoes` | `{ roleKey, departmentId? }` → 201 | `ROLE:MANAGE` (TO) |
| DELETE | `/api/v1/usuarios/{id}/concessoes/{concessaoId}` | — | `ROLE:MANAGE` (TO) |
| GET / POST | `/api/v1/convites` | POST: `{ email, roleKey, departmentId?, hierarchyLevel? }` → inclui `token` | `INVITE:MANAGE` (BT) |
| POST | `/api/v1/convites/{id}/reenviar` | — | `INVITE:MANAGE` |
| DELETE | `/api/v1/convites/{id}` | — | `INVITE:MANAGE` |
| POST | `/api/v1/convites/aceitar` | `{ token, name?, password }` | público |

### 2.2 Departamentos (`department`)

| Método | Caminho | Corpo | Autorização |
|---|---|---|---|
| GET / POST | `/api/v1/departamentos` | POST: `{ name, description?, monthlyBudget? }` | `DEPARTMENT:READ` / `DEPARTMENT:MANAGE` |
| GET / PUT / DELETE | `/api/v1/departamentos/{id}` | DELETE é lógico (inativa) | `DEPARTMENT:READ` (BT) / `DEPARTMENT:MANAGE` |
| PUT | `/api/v1/departamentos/{id}/orcamentos/{mes}` | `{ amount, alertThresholdPct? }` (`mes=AAAA-MM`) | `DEPARTMENT:MANAGE` (BT) |
| GET | `/api/v1/departamentos/{id}/orcamentos` | `de=AAAA-MM&ate=AAAA-MM` (≤ 24 meses) | `DEPARTMENT:READ` (BT) |

### 2.3 Catálogo de IA e roteamento (`aigateway`)

| Método | Caminho | Autorização |
|---|---|---|
| GET / POST | `/api/v1/ai-gateway/providers` | `AI_TOOL:MANAGE` |
| GET / POST | `/api/v1/ai-gateway/models` | `AI_TOOL:MANAGE` |
| PATCH | `/api/v1/ai-gateway/models/{id}/status` | `AI_TOOL:MANAGE` |
| PUT | `/api/v1/ai-gateway/models/{id}` | `AI_TOOL:MANAGE` |
| GET / PUT | `/api/v1/ai-gateway/departments/{id}/model-policy` | `AI_TOOL:MANAGE` |
| GET | `/api/v1/ai-gateway/models/permitido?requestedModelId=` | autenticado (usa a lotação do usuário) |

- `GET /models` inclui `inputPricePer1k` e `outputPricePer1k`; a tela administrativa
  usa esses campos e o nome do provider (`GET /providers`) para listar o catálogo.
- `PUT /models/{id}` edita `name`, `externalName` e preços; não altera status nem
  provider. Preço negativo → 400; nome duplicado no tenant → 409. Modelo `AVAILABLE`
  continua exigindo os dois preços (S4-D12); custos passados não mudam.
- `GET /departments/{id}/model-policy` devolve `{ departmentId, defaultModelId,
  allowedModelIds[] }`; sem política, lista vazia e padrão nulo. Departamento de outro
  tenant → 404. O `PUT` mantém o invariante ADR-08 (padrão ∈ permitidos).
- A tela `/onboarding/ia` usa esses endpoints, exige `AI_TOOL:MANAGE`, nunca pede nem
  exibe segredo (`credentialRef` é apenas o nome da variável) e não usa fallback de
  `localStorage` nem sucesso simulado.

### 2.4 Uso e dashboard (`observability`)

| Método | Caminho | Filtros | Autorização |
|---|---|---|---|
| GET | `/api/v1/observability/usage` | `departmentId`, `userId`, `from`, `to`, `page`, `size` | `USAGE:READ` (BT; filtro fora do escopo → 403) |
| GET | `/api/v1/observability/metrics/usage` | `from`, `to`, `departmentId`, `modelId`, `userId`, `granularity` | `USAGE:READ` (BT) |
| GET | `/api/v1/observability/metrics/costs` | `from`, `to`, `departmentId`, `modelId`, `userId`, `groupBy=DEPARTMENT\|MODEL\|USER` | `USAGE:READ` (BT) |
| GET | `/api/v1/observability/orcamentos` | `mes=AAAA-MM` | `USAGE:READ` (BT; SelfOnly → 403) |
| GET | `/api/v1/ai-gateway/tentativas` | `de`, `ate`, `status`, `deliveryStatus`, `costStatus`, `userId`, `departmentId`, `page`, `size` | `USAGE:READ` (BT) |

### 2.5 Infra

| Método | Caminho | Autorização |
|---|---|---|
| GET | `/actuator/health` | público |
| GET | `/swagger-ui.html`, `/v3/api-docs` | público (desligável por `OPENAPI_ENABLED`) |

### 2.6 Chamadas do front em 20/10/2026

O cliente do browser é `public/js/**` (`window.ApiClient` base `/api` + `/v1/...` e `window.API`
base `/api/v1`). Os controllers server-side `modules/*/*.controller.js` continuam inalcançáveis:
o proxy `app.use("/api", …)` (`app.js:76`) é registrado antes das rotas dos módulos.

| Arquivo | Chamada | Status |
|---|---|---|
| `core/auth.js` | `POST /api/v1/auth/login` | ✅ |
| `pages/onboarding/step-tenant.js` | `POST /api/v1/tenants` + `POST /api/v1/auth/login` | ✅ (registra e autentica o admin) |
| `pages/aceite-convite.js` | `POST /api/v1/convites/aceitar` | ✅ |
| `pages/admin-convites.js` | `GET /me/permissoes`, `GET /papeis`, `GET /departamentos`, `GET/POST /convites`, `POST …/{id}/reenviar`, `DELETE …/{id}` | ✅ |
| `pages/admin-usuarios.js` | `GET /me/permissoes`, `GET /usuarios`, `POST …/{id}/nivel`, `PATCH /usuarios/{id}`, `GET …/{id}/permissoes`, `POST/DELETE …/concessoes` | ✅ |
| `pages/editar-empresa.js` | `GET/PUT /api/v1/tenants/me` | ✅ |
| `pages/identidade-visual.js` | `GET/PUT /api/v1/tenant/identidade-visual` | ❌ **path errado** (singular). Correto: `/api/v1/tenants/me/identidade-visual` |
| `pages/dashboard.js` | `GET /api/v1/observability/metrics/usage` e `/costs`, `/orcamentos`, `/ai-gateway/tentativas` | ✅ |
| `pages/orcamentos.js` | `GET/PUT /departamentos/{id}/orcamentos`, `GET /observability/orcamentos`, `GET /me/permissoes` | ✅ |
| `modules/*/*.controller.js` (server-side, inalcançável) | `/ia`, `/revisao`, `/revisao/confirmar`, `/auth/redefinir-senha`, `/usuarios/importar`, `GET /tenants` | ❌ não existem no backend; código morto |

## 2.7 Payloads de autorização (SPEC-01 §7)

`POST /api/v1/tenants`:

```json
{ "slug": "acme", "name": "ACME", "emailDomain": "acme.com",
  "adminName": "Ana", "adminEmail": "ana@acme.com", "adminPassword": "..." }
```

- `adminEmail` deve pertencer a `emailDomain`; senha ≥ 8 caracteres; resposta 201 sem senha.
- Tudo em uma transação: tenant, papéis, usuário e concessão `TENANT_ADMIN`/TENANT (BOOTSTRAP).

`POST /api/v1/convites`:

```json
{ "email": "x@acme.com", "roleKey": "LEADER",
  "departmentId": "<uuid|null>", "hierarchyLevel": "LEADER" }
```

- `departmentId` é obrigatório para papéis `DEPARTMENT`; `hierarchyLevel` é opcional (derivado
  do papel quando ausente).
- A resposta 201 inclui `token` **uma única vez**; só o hash SHA-256 fica no banco e o token
  nunca vai para log. `GET /convites` nunca devolve token.

`GET /api/v1/usuarios/me/permissoes` e `/{id}/permissoes`:

```json
{ "userId": "…",
  "concessoes": [{ "id": "…", "papel": "MANAGER", "escopo": "DEPARTMENT",
                   "departmentId": "…", "origem": "INVITE" }],
  "permissoes": ["USER:READ", "USAGE:READ"] }
```

- `permissoes` é a união sem escopo, usada só para menus; não é autorização.

`POST /api/v1/usuarios/{id}/concessoes`: `{ "roleKey": "MANAGER", "departmentId": "…" }`.
Respostas: 201 criada; 409 concessão ativa idêntica; 403 ator = alvo.

`GET /api/v1/papeis`: `[{ "key": "LEADER", "escopo": "DEPARTMENT", "posto": 2, "concedivel": true }]`.

---

> **Registro histórico (27/09/2026).** O conteúdo abaixo mostra a reconciliação planejada naquela data. Vários itens já foram resolvidos; o estado atual está nas §1–§2.

## 3. Endpoints que o frontend consome hoje (e o status)

### 3.1 Browser (ativo) — `public/js/**`
Cliente `window.ApiClient` (base `/api`) e `window.API` (base `/api/v1`).

| Arquivo | Chamada atual | URL efetiva | Backend | Status |
|---|---|---|---|---|
| `core/auth.js` | `API.post("/auth/login")` | `/api/v1/auth/login` | `POST /api/v1/auth/login` | ✅ ok |
| `pages/aceite-convite.js` | `ApiClient.post('/auth/aceitar-convite')` | `/api/auth/aceitar-convite` | `POST /api/v1/convites/aceitar` | ❌ path errado |
| `pages/admin-convites.js` | `ApiClient.post('/v1/convites')` | `/api/v1/convites` | `POST /api/v1/convites` | ✅ ok |
| `pages/admin-convites.js` | `ApiClient.post('/v1/convites/{id}/reenviar')` | `/api/v1/convites/{id}/reenviar` | — | ❌ inexistente |
| `pages/admin-convites.js` | `ApiClient.delete('/v1/convites/{id}')` | `/api/v1/convites/{id}` | — | ❌ inexistente |
| `pages/admin-usuarios.js` | `ApiClient.get('/v1/usuarios')` | `GET /api/v1/usuarios` | só `POST` | ❌ falta list |
| `pages/admin-usuarios.js` | `ApiClient.put('/v1/usuarios/{id}/permissao')` | `/api/v1/usuarios/{id}/permissao` | — | ❌ inexistente |
| `pages/editar-empresa.js` | `API.get("/tenant/me")` / `API.put("/tenant/me")` | `/api/v1/tenant/me` | — | ❌ naming + inexistente |
| `pages/identidade-visual.js` | `API.get/put("/tenant/identidade-visual")` | `/api/v1/tenant/identidade-visual` | — | ❌ inexistente |

### 3.2 Server-side (morto — shadowed pelo proxy `/api`)
Os `modules/*/*.controller.js` (axios → `/api/v1/...`) **nunca executam** porque `app.use("/api", ...)` é registrado antes das rotas. Ainda assim, registram o contrato esperado:

| Controller | Chamadas | Backend | Status |
|---|---|---|---|
| `admin.controller.js` | `/admin/convites` (GET/POST/DELETE) | `/api/v1/convites` | ❌ naming |
| `auth.controller.js` | `/auth/login` ✅ · `/auth/redefinir-senha` · `/auth/aceitar-convite` | login ✅; demais ❌ | parcial |
| `departamentos.controller.js` | `/departamentos/**` | — | ❌ inexistente |
| `ia.controller.js` | `/ia`, `/ai-gateway/providers`, `/ai-gateway/models`, `/ai-gateway/models/{id}/status` | — | ❌ inexistente |
| `revisao.controller.js` | `/revisao`, `/revisao/confirmar` | — | ❌ inexistente |
| `tenant.controller.js` | `POST /tenants` ✅ · `GET /tenants` · `/tenants/me` · `/tenants/me/identidade-visual` | parcial | parcial |
| `usuarios.controller.js` | `POST /usuarios` ✅ · `GET /usuarios` · `/usuarios/importar` · `PUT /usuarios/{id}` | parcial | parcial |

---

## 4. Reconciliação (Opção A) — frontend → contrato canônico

### 4.1 Ajustes no **frontend** (sem depender de novo endpoint)
| Frontend hoje | Deve usar (canônico) | Observação |
|---|---|---|
| `ApiClient` base `/api` + `'/v1/...'` | **um único cliente** base `/api/v1` + `'/...'` | elimina a duplicidade e o bug de prefixo |
| `aceite-convite.js`: `/auth/aceitar-convite` | `POST /api/v1/convites/aceitar` | path errado hoje |
| `tenant.controller.js`: `/tenants/me` | `GET/PUT /api/v1/tenants/me` | canônico (plural) |
| `editar-empresa.js`: `/tenant/me` | `GET/PUT /api/v1/tenants/me` | idem |
| `identidade-visual.js`: `/tenant/identidade-visual` | `GET/PUT /api/v1/tenants/me/identidade-visual` | idem |
| `admin.controller.js`: `/admin/convites` | `/api/v1/convites` | naming |
| `admin-usuarios.js`: `PUT /usuarios/{id}/permissao` | `POST /api/v1/usuarios/{id}/nivel` (hierarquia) e/ou endpoint de permissões | definir |
| `usuarios.controller.js`: `PUT /usuarios/{id}` | `PATCH /api/v1/usuarios/{id}` | atualização de perfil |

### 4.2 Endpoints a **implementar no backend** (nomenclatura canônica; prioridade backend)
| Recurso | Endpoint canônico proposto | Autorização |
|---|---|---|
| Listar usuários | ✅ `GET /api/v1/usuarios` (page/size, filtros `hierarchyLevel`, `departmentId`, `name`, `email`) | `USER:READ` |
| Editar **Department** do usuário | ✅ `PATCH /api/v1/usuarios/{id}` `{ departmentId }` — **só** o department; o papel/nível fica em `POST /usuarios/{id}/nivel` (ADR-33) | `USER:MANAGE` |
| Desativar/reativar usuário | ✅ `PATCH /api/v1/usuarios/{id}/status` `{ status }` | `USER:MANAGE` |
| Reenviar convite | ✅ `POST /api/v1/convites/{id}/reenviar` | `INVITE:MANAGE` |
| Revogar convite | ✅ `DELETE /api/v1/convites/{id}` | `INVITE:MANAGE` |
| Tenant atual | ✅ `GET /api/v1/tenants/me` (SPI-52) | autenticado |
| Atualizar tenant | ✅ `PUT /api/v1/tenants/me` `{ name }` (SPI-52) | `TENANT:MANAGE` |
| Status do tenant | ⛔ `PATCH /api/v1/tenants/me/status` — **não implementado**: o bloqueio (SPI-57) já é garantido; alterar o status exige ator de plataforma (ADR) | — |
| Identidade visual | `GET/PUT /api/v1/tenants/me/identidade-visual` (SPI-53, exige migration) | `TENANT:MANAGE` |
| Redefinir senha | `POST /api/v1/auth/redefinir-senha` | público (fora do MVP — confirmar) |
| Departamentos | `/api/v1/departamentos/**` | `DEPARTMENT:MANAGE`/`READ` |
| Catálogo de IA | `/api/v1/ia`, `/api/v1/ai-gateway/{providers,models}` | `AI_TOOL:MANAGE`/`READ` |
| Revisão de onboarding | `/api/v1/revisao`, `/api/v1/revisao/confirmar` | autenticado |

> Observação: `departamentos`, `ia`/`ai-gateway` e `revisao` pertencem a módulos que **ainda não existem** (`department`, `aigateway`) — dependem das tasks de EPIC-002/sprint-2. Nesta reconciliação, essas telas permanecem **visual-only** até os módulos existirem.

---

## 5. Ordem de execução sugerida (prioridade backend)

1. ✅ **Migrations**: colisão `V005×2` resolvida — migrations agora `V001`–`V006` únicas.
   (`V006×2` só aparecerá ao mergear a branch de frontend; resolver na ocasião.)
2. ✅ **Modelo RBAC (SPI-39)**: adotado **papéis por tenant** (`iam_roles`/`iam_role_permissions`/`iam_role_assignments`);
   removido o modelo `permission_grant` (órfão). Decisão registrada em `ai/adr/ADR-33-authorization-model-roles.md`.
3. ⏳ **Backend — IAM (existente)**: POPROJ-14 entregue — `GET /api/v1/usuarios` (SPI-62), `PATCH /api/v1/usuarios/{id}` (SPI-63), `PATCH /api/v1/usuarios/{id}/status` (SPI-61), `POST /api/v1/convites/{id}/reenviar` (SPI-59) e `DELETE /api/v1/convites/{id}`.
   SPI-52 entregue (`GET/PUT /api/v1/tenants/me`; `TENANT:MANAGE` concedida ao EXECUTIVE na matriz padrão). SPI-57: o bloqueio de login e de requests protegidos para tenant INACTIVE já era garantido e agora tem cobertura de IT; **não** há endpoint de mudança de status — o ator "admin de plataforma" não existe no código e criá-lo exige ADR. SPI-54 entregue: `emailDomain` obrigatório em `POST /api/v1/tenants`, único globalmente (409 em sobreposição); o login resolve o tenant pelo domínio cadastrado (ADR-32 emendado). Pendente: `GET/PUT /api/v1/tenants/me/identidade-visual` (SPI-53, exige migration).
4. **Frontend — adaptar** (§4.1): unificar o cliente HTTP, corrigir paths/verbos e remover a camada server-side morta (ou repontá-la).
5. **Backend — módulos futuros** (`department`, `aigateway`, `revisao`): implementar quando as tasks existirem; telas visual-only até lá.
6. **Testes**: por endpoint novo, teste de API (401/403/200) + IT de tenant isolation; ArchUnit verde.

---

## 6. Critérios de aceite

- [ ] Um único contrato REST canônico documentado (este arquivo) e seguido pelo frontend.
- [ ] Nenhum endpoint do frontend aponta para rota inexistente **sem** estar marcado como "visual-only".
- [ ] Migrations Flyway sem versões duplicadas.
- [ ] `mvn test` + `mvn verify -Pintegration` verdes.
- [ ] Nenhum segredo/hardcode reintroduzido (JWT obrigatório; `ddl-auto=validate`).

---

## 7. Riscos

- **Contrato duplicado**: a branch de frontend implementou endpoints próprios; ao adotar a Opção A, parte desse trabalho é **descartado/adaptado**.
- **Modelo RBAC concorrente** (`permission_grant` × roles): decisão pendente afeta `UserController`/`TenantController`.
- **Módulos ausentes** (`department`, `aigateway`): telas ficam visual-only; não bloquear a entrega por isso.
