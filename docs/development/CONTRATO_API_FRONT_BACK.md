# Contrato de API — Frontend × Backend (reconciliação)

> **Objetivo:** documentar o contrato REST entre o frontend (Express/EJS + JS de browser) e o backend (Spring Boot) e propor a **reconciliação pela Opção A**: o **backend é a fonte de verdade** e o **frontend se adapta** ao contrato canônico.
>
> **Status:** proposta (planejamento). Nenhuma alteração de código foi feita ainda.

---

## 1. Princípios

1. **Backend é a fonte de verdade.** O contrato REST canônico segue ADR-28: prefixo `/api/v1`, controllers por módulo, `REST → application use case`.
2. **Tenant nunca vem do cliente.** O `tenantId` é derivado do `TenantContext` (JWT); o frontend não envia `tenantId`.
3. **Autorização é do backend.** Endpoints protegidos usam RBAC granular (`@RequiresPermission`) e respondem `401` (sem token) / `403` (sem permissão).
4. **O frontend se adapta** (Opção A): os caminhos/verbos do frontend são ajustados ao contrato canônico; onde o backend ainda não expõe um recurso necessário, o backend o implementa **com a nomenclatura canônica** (prioridade backend).
5. **Um único cliente HTTP no browser** (base `/api/v1`), eliminando a duplicidade atual (`window.API` × `window.ApiClient`).

---

## 2. Contrato canônico do backend (endpoints existentes)

| Método | Caminho | Corpo (request) | Resposta | Autorização |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | `{ tenantSlug?, email, password }` | `{ token, tokenType }` | público |
| POST | `/api/v1/tenants` | `{ slug, name }` | tenant | público (onboarding) |
| POST | `/api/v1/usuarios` | `{ email, name, hierarchyLevel, password, departmentId? }` | usuário | `USER:MANAGE` |
| POST | `/api/v1/usuarios/{id}/nivel` | `{ hierarchyLevel }` | usuário | `USER:MANAGE` |
| GET | `/api/v1/usuarios/me/permissoes` | — | `{ userId, permissions[] }` | autenticado |
| GET | `/api/v1/usuarios/{id}/permissoes` | — | `{ userId, permissions[] }` | `USER:READ` |
| POST | `/api/v1/convites` | `{ email, hierarchyLevel, departmentId? }` | convite (com `token`) | `INVITE:MANAGE` |
| POST | `/api/v1/convites/aceitar` | `{ token, name?, password }` | usuário | público |
| GET | `/actuator/health` | — | health | público |
| GET | `/swagger-ui.html`, `/v3/api-docs` | — | Swagger/OpenAPI | público (desabilitável por `OPENAPI_ENABLED`) |

Convenções: `/api/v1/{resource}` (plural), paginação `page`/`size` (0-based, default 20, máx 100), erros `{ "message": "..." }` sem vazar internals.

---

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
| Editar papel/departamento do usuário | ✅ `PATCH /api/v1/usuarios/{id}` `{ hierarchyLevel?, departmentId? }` | `USER:MANAGE` |
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
   SPI-52 entregue (`GET/PUT /api/v1/tenants/me`; `TENANT:MANAGE` concedida ao EXECUTIVE na matriz padrão). SPI-57: o bloqueio de login e de requests protegidos para tenant INACTIVE já era garantido e agora tem cobertura de IT; **não** há endpoint de mudança de status — o ator "admin de plataforma" não existe no código e criá-lo exige ADR. Pendente: `GET/PUT /api/v1/tenants/me/identidade-visual` (SPI-53, exige migration).
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
