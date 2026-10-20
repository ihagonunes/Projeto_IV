# Matriz de permissões (SPI-51)

Estado conferido contra o código da Sprint 3 (ADRs 33/34/36/37). A fonte de verdade é
`SystemRole` + as migrations `V005`/`V015`; este documento é a leitura humana da matriz.

## 1. Papéis de sistema

Não existem papéis customizados. `iam_roles.name` é a chave do papel.

| Papel | Escopo | Posto | Permissões (match exato — `MANAGE` não implica outras ações) |
|---|---|---|---|
| `COLLABORATOR` | SELF | 1 | CHAT:CREATE, CHAT:READ, KNOWLEDGE_DOCUMENT:READ, USAGE:READ, MODEL:READ |
| `LEADER` | DEPARTMENT | 2 | COLLABORATOR + USER:READ, GOVERNANCE_SCORE:READ |
| `MANAGER` | DEPARTMENT | 3 | LEADER + USER:MANAGE, INVITE:MANAGE, ROLE:READ, DEPARTMENT:READ, PROJECT:MANAGE, AI_TOOL:READ |
| `EXECUTIVE` | TENANT | 4 | MANAGER + TENANT:READ |
| `TENANT_ADMIN` | TENANT | 100 (fora da escada) | READ+MANAGE em TENANT, ROLE, USER, INVITE, DEPARTMENT, AI_TOOL, MODEL, KNOWLEDGE_DOCUMENT; LICENSE:MANAGE, TRAINING:MANAGE, USAGE:READ, GOVERNANCE_SCORE:READ. **Sem CHAT.** |

Contagens de controle: 5, 7, 13, 14 e 20 permissões, respectivamente.

Observações:

- `EXECUTIVE` **não** tem TENANT:MANAGE, AI_TOOL:MANAGE, DEPARTMENT:MANAGE, ROLE:MANAGE,
  MODEL:MANAGE, LICENSE:MANAGE nem TRAINING:MANAGE. Um diretor que também administra recebe
  duas concessões (`EXECUTIVE` + `TENANT_ADMIN`).
- O rótulo `hierarchyLevel` (`COLLABORATOR`/`LEADER`/`MANAGER`/`EXECUTIVE`) é organizacional e
  **não** autoriza nada; o escopo vem da concessão.
- O teto efetivo de `CHAT` é sempre `SELF`, mesmo para concessões `TENANT`/`DEPARTMENT`.

## 2. Regras de alvo (`targeting`)

`@RequiresPermission` declara `resource`, `action` e `targeting`:

| Targeting | Gate | Uso |
|---|---|---|
| `TENANT_ONLY` (padrão) | exige concessão aplicável com teto efetivo `TENANT` | operações administrativas globais do tenant |
| `BY_TARGET` | exige qualquer concessão aplicável; o **caso de uso** faz a checagem fina | recursos de um departamento/usuário/conversa específicos |

Chamada fina (ADR-36):

| Tipo de alvo | Cobertura |
|---|---|
| `TenantTarget` | qualquer concessão ativa do ator |
| `DepartmentTarget(d)` | `TENANT` ou `DEPARTMENT` com `departmentId = d` |
| `OwnedTarget(userId, dept)` | `TENANT`; `DEPARTMENT` quando `dept` está no escopo; `SELF` quando `userId` é o ator |

`visibilityFor` (consultas agregadas):

| Situação | Escopo |
|---|---|
| nenhuma concessão aplicável | `None` → 403 |
| alguma concessão com teto `TENANT` | `TenantWide` |
| alguma com teto `DEPARTMENT` | `Departments(ids, self)` → linhas dos ids **ou** do próprio usuário |
| somente `SELF` | `SelfOnly(self)` |

Filtro pedido fora do escopo (`departmentId`, `userId`) → **403**. Recurso de outro tenant →
**404**. As concessões são lidas do banco a cada requisição, sem cache: a revogação vale na
requisição seguinte.

## 3. Endpoints e gates

`TO` = `TENANT_ONLY`; `BT` = `BY_TARGET` com checagem fina no caso de uso.

| Método e rota | Permissão | Targeting | Regra fina |
|---|---|---|---|
| POST `/api/v1/auth/login` | público | — | `tenantSlug` opcional; tenant pelo domínio do e-mail (ADR-32) |
| POST `/api/v1/tenants` | público | — | cria tenant, papéis, admin `TENANT_ADMIN` e concessão BOOTSTRAP |
| POST `/api/v1/convites/aceitar` | público | — | revalida a autoridade do convidante |
| GET `/api/v1/tenants/me` | autenticado | — | tenant do JWT |
| PUT `/api/v1/tenants/me` (+ identidade visual) | TENANT:MANAGE | TO | — |
| GET `/api/v1/usuarios` | USER:READ | BT | `visibilityFor(USER:READ)`: Departments ⇒ depto no escopo ou self; SelfOnly ⇒ self |
| PATCH `/api/v1/usuarios/{id}` | USER:MANAGE | BT | `domina(ator, alvo)` e novo depto coberto |
| PATCH `/api/v1/usuarios/{id}/status` | USER:MANAGE | BT | `domina`; último admin → 409; ator = alvo → 403 |
| POST `/api/v1/usuarios/{id}/nivel` | USER:MANAGE | BT | `domina`; rótulo sem efeito de autorização |
| GET `/api/v1/usuarios/me/permissoes` | autenticado | — | concessões + união de permissões (só UX) |
| GET `/api/v1/usuarios/{id}/permissoes` | USER:READ | BT | `isAllowed(OwnedTarget(id, deptAtual))` |
| GET `/api/v1/papeis` | ROLE:READ | BT | 5 papéis com `concedivel` para o ator |
| POST `/api/v1/usuarios/{id}/concessoes` | ROLE:MANAGE | TO | só `TENANT_ADMIN`; ator ≠ alvo; origem DIRECT; duplicada → 409 |
| DELETE `/api/v1/usuarios/{id}/concessoes/{concessaoId}` | ROLE:MANAGE | TO | ator ≠ alvo; último admin → 409; revogação lógica |
| GET/POST `/api/v1/convites` | INVITE:MANAGE | BT | lista/cria só o que o ator pode conceder |
| POST `/api/v1/convites/{id}/reenviar`, DELETE `/api/v1/convites/{id}` | INVITE:MANAGE | BT | ator ainda pode conceder o papel do convite |
| POST `/api/v1/usuarios` | — | — | **removido** (404/405); criação só por convite |
| GET/POST `/api/v1/departamentos` | DEPARTMENT:READ / MANAGE | READ=BT, MANAGE=TO | lista filtrada por `visibilityFor(DEPARTMENT:READ)` |
| GET/PUT/DELETE `/api/v1/departamentos/{id}` | DEPARTMENT:READ / MANAGE | READ=BT, MANAGE=TO | `isAllowed(DepartmentTarget(id))` na leitura |
| PUT `/api/v1/departamentos/{id}/orcamentos/{mes}` | DEPARTMENT:MANAGE | BT | `isAllowed(DepartmentTarget(id))`; outro tenant → 404 |
| GET `/api/v1/departamentos/{id}/orcamentos` | DEPARTMENT:READ | BT | `isAllowed(DepartmentTarget(id))`; intervalo ≤ 24 meses |
| `/api/v1/ai-gateway/providers`, `/models`, `/models/{id}/status`, `/departments/{id}/model-policy` | AI_TOOL:MANAGE | TO | — |
| GET `/api/v1/ai-gateway/models/permitido` | autenticado | — | usa a lotação/ModelPolicy do ator |
| GET `/api/v1/ai-gateway/tentativas` | USAGE:READ | BT | `visibilityFor(USAGE:READ)`; `userId`/depto fora do escopo → 403 |
| GET `/api/v1/observability/usage` | USAGE:READ | BT | `visibilityFor(USAGE:READ)`; filtros fora do escopo → 403 |
| GET `/api/v1/observability/metrics/usage` | USAGE:READ | BT | `visibilityFor(USAGE:READ)`; `departmentId` fora → 403 |
| GET `/api/v1/observability/metrics/costs` | USAGE:READ | BT | idem; `groupBy=DEPARTMENT\|MODEL\|USER`; `userId` fora → 403 |
| GET `/api/v1/observability/orcamentos` | USAGE:READ | BT | TenantWide ⇒ todos; Departments ⇒ ids; SelfOnly ⇒ 403 |
| GET `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs` | público | — | desligável por configuração |

## 4. Erros de autorização

| Situação | Status |
|---|---|
| Recurso de outro tenant | 404 |
| Sem permissão no gate; fora do escopo; filtro fora do escopo; ator = alvo; não domina | 403 |
| Último `TENANT_ADMIN`; convidante perdeu autoridade no aceite; concessão duplicada | 409 |
| Token de convite inválido, expirado ou reutilizado | 400 |
