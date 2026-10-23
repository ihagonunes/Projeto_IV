# SPEC-01 — Autorização por concessão com escopo (ADR-36 / ADR-37)

Especificação de implementação das tarefas S3-A1…S3-A10. Em caso de dúvida, prevalecem os ADRs 36 e 37.
Os fatos do código atual estão em `docs/review/VERIFICACAO_FASE0.md` (linhas citadas lá).

---

## 1. Papéis de sistema

O nome do papel (`iam_roles.name`) é a chave do papel de sistema. Não há papéis customizados.

| Papel | Escopo | Posto | Permissões (match exato — `MANAGE` não implica outras ações) |
|---|---|---|---|
| `COLLABORATOR` | SELF | 1 | CHAT:CREATE, CHAT:READ, KNOWLEDGE_DOCUMENT:READ, USAGE:READ, MODEL:READ |
| `LEADER` | DEPARTMENT | 2 | COLLABORATOR + USER:READ, GOVERNANCE_SCORE:READ |
| `MANAGER` | DEPARTMENT | 3 | LEADER + USER:MANAGE, INVITE:MANAGE, ROLE:READ, DEPARTMENT:READ, PROJECT:MANAGE, AI_TOOL:READ |
| `EXECUTIVE` | TENANT | 4 | MANAGER + TENANT:READ |
| `TENANT_ADMIN` | TENANT | 100 (fora da escada) | READ+MANAGE em TENANT, ROLE, USER, INVITE, DEPARTMENT, AI_TOOL, MODEL, KNOWLEDGE_DOCUMENT; LICENSE:MANAGE, TRAINING:MANAGE. **Sem CHAT, USAGE ou GOVERNANCE_SCORE (emenda ADR-36, V021).** |

Contagens de controle para os testes: 5, 7, 13, 14, 18.

`EXECUTIVE` **perde** TENANT:MANAGE, AI_TOOL:MANAGE, DEPARTMENT:MANAGE, ROLE:MANAGE, MODEL:MANAGE,
LICENSE:MANAGE e TRAINING:MANAGE. Um diretor que também administra recebe as duas concessões.

## 2. Contratos

### 2.1 `sharedkernel.rbac` (novos)

```java
public enum ScopeType { SELF, DEPARTMENT, TENANT }

public sealed interface AccessTarget {
    record TenantTarget() implements AccessTarget {}
    record DepartmentTarget(UUID departmentId) implements AccessTarget {}     // departmentId != null
    record OwnedTarget(UserId owner, UUID ownerDepartment) implements AccessTarget {} // owner != null; dept pode ser null
}

public sealed interface VisibilityScope {
    record TenantWide() implements VisibilityScope {}
    record Departments(Set<UUID> ids, UserId self) implements VisibilityScope {} // ids não vazio; linhas de ids OU do self
    record SelfOnly(UserId self) implements VisibilityScope {}
    record None() implements VisibilityScope {}
    default boolean coversDepartment(UUID departmentId); // TenantWide → true; Departments → ids.contains; demais → false
}
```

Departamento é `UUID`, como em todo o código. Não existe `DepartmentId` no `sharedkernel.id`, e não
deve ser criado.

### 2.2 `iam.api` (contrato alterado — incompatível)

```java
// iam.api.query.AuthorizationQuery — substitui checkPermission/resolveScope
public interface AuthorizationQuery {
    boolean isAllowed(TenantId tenantId, UserId actor, Resource resource, Action action, AccessTarget target);
    VisibilityScope visibilityFor(TenantId tenantId, UserId actor, Resource resource, Action action);
}

// iam.api.rbac
public enum Targeting { TENANT_ONLY, BY_TARGET }

public @interface RequiresPermission {
    Resource resource();
    Action action();
    Targeting targeting() default Targeting.TENANT_ONLY;
}
```

Removidos em S3-A8: `iam.api.model.Permission` (enum legado), `AuthorizationScope`,
`AuthorizationQuery.checkPermission` e `AuthorizationQuery.resolveScope`.
`AuthorizationResult` pode continuar como tipo de retorno interno do avaliador.

### 2.3 Domínio `iam.domain.rbac`

- `SystemRole` (enum): escopo, posto e permissões da tabela §1; `fromKey(String)`;
  `isDelegableByNonAdmins()` (só COLLABORATOR e LEADER). **Substitui `PermissionMatrix`**, que é
  removida junto com o seu teste.
- `GrantOrigin` (enum): `BOOTSTRAP`, `INVITE`, `DIRECT`, `MIGRATION`.
- `Role`: ganha `Optional<SystemRole> systemRole()`, derivado de `name`.
- `RoleAssignment` (concessão), record com:
  - campos: `id, userId, roleId, tenantId, scopeType, departmentId, origin, grantedBy, grantedAt,
    revokedAt, revokedBy`;
  - invariante: `departmentId != null` ⇔ `scopeType == DEPARTMENT`;
  - `static grant(UserId, Role, UUID departmentId, GrantOrigin, UserId grantedBy, Instant now)`: usa
    o escopo fixo do papel e descarta o departamento quando o escopo não é DEPARTMENT;
  - `isActive()` (`revokedAt == null`);
  - `revoke(UserId actor, Instant now)`: idempotente;
  - `covers(AccessTarget)`:
    - TENANT → sempre;
    - DEPARTMENT(d) → `DepartmentTarget(d)` ou `OwnedTarget(_, d)`;
    - SELF → `OwnedTarget(userId, _)`.

## 3. Regras do avaliador (`iam.application.rbac.PolicyEvaluator`)

Pré-condição comum: usuário ACTIVE, mesmo tenant, tenant ACTIVE. Caso contrário, nega (como hoje).

```
concessõesAplicáveis(ctx, perm) =
  concessões ATIVAS de (tenant, ator)
  ∩ papel.tenantId == ctx.tenant          // defesa contra papel de outro tenant (D15)
  ∩ papel.permissions contém perm

tetoEfetivo(c, resource) = resource == CHAT ? SELF : c.scopeType   // ADR-36 item 5

isAllowed(ctx, perm, alvo)  = ∃ c ∈ aplicáveis : coberturaComTeto(c, alvo)
gateTenantOnly(ctx, perm)   = ∃ c ∈ aplicáveis : tetoEfetivo(c) == TENANT
gateByTarget(ctx, perm)     = aplicáveis ≠ ∅
visibilityFor(ctx, perm):
  aplicáveis = ∅                             → None
  algum com tetoEfetivo TENANT              → TenantWide
  algum com tetoEfetivo DEPARTMENT          → Departments({depts}, self)
  senão (só SELF)                           → SelfOnly(self)
```

As concessões são lidas do banco a cada chamada, sem cache, e a revogação vale na requisição
seguinte (AZ11).

## 4. Delegação, dominância e guardas (ADR-37)

```
podeConceder(ator, papel R, depto d):
  ator tem TENANT_ADMIN ativo                     → true
  R.isDelegableByNonAdmins()
    && ∃ c do ator: c.ativa && rank(c) > rank(R)
       && c tem INVITE:MANAGE (convite) ou ROLE:MANAGE (direta)
       && escopoContém(c, R, d)                   → true
  senão                                           → false

escopoContém(c, R, d):
  c.scope == TENANT                               → true
  c.scope == DEPARTMENT && R.scope ∈ {SELF, DEPARTMENT} && d == c.departmentId → true
  senão false

domina(ator, alvo):        // para editar, desativar, revogar ou mudar depto/rótulo
  ator == alvo                                    → false (guarda fixa → 403)
  ator tem TENANT_ADMIN                           → true
  ∀ c ativa do alvo: ∃ a do ator com rank(a) > rank(c) e escopoContém(a, papel(c), depto(c))
  e o alvo está lotado num depto coberto pelo ator
```

Guardas fixas:

- ator == alvo em mudança de rótulo, depto, status ou concessão → **403**;
- revogar ou desativar o último `TENANT_ADMIN` ativo do tenant → **409**;
- mover usuário para um depto fora do escopo do ator → **403** (AZ14).

## 5. Migration V015 (`V015__iam_scoped_grants.sql`)

Requisitos: PostgreSQL ≥ 13 (`gen_random_uuid`, `sha256` nativos; compose e testes usam 18.6).
Abortar com `RAISE EXCEPTION` em qualquer ambiguidade.

```sql
-- V015 - IAM: concessões com escopo explícito (ADR-36/ADR-37).

-- 0) Abortos (ambiguidade é decidida por pessoa, nunca por regra permissiva)
DO $$
DECLARE n INTEGER;
BEGIN
    SELECT count(*) INTO n FROM iam_roles
     WHERE name NOT IN ('COLLABORATOR','LEADER','MANAGER','EXECUTIVE','TENANT_ADMIN');
    IF n > 0 THEN RAISE EXCEPTION 'V015: % papel(is) fora dos papéis de sistema.', n; END IF;

    SELECT count(*) INTO n FROM (
        SELECT user_id FROM user_permission GROUP BY user_id
        HAVING count(DISTINCT permission) BETWEEN 1 AND 8) p;
    IF n > 0 THEN RAISE EXCEPTION 'V015: % usuário(s) com permissões legadas parciais.', n; END IF;

    SELECT count(*) INTO n FROM iam_role_assignments a JOIN iam_roles r ON r.id = a.role_id
     WHERE (r.name IN ('LEADER','MANAGER') AND a.department_id IS NULL)
        OR (r.name IN ('COLLABORATOR','EXECUTIVE','TENANT_ADMIN') AND a.department_id IS NOT NULL);
    IF n > 0 THEN RAISE EXCEPTION 'V015: % atribuição(ões) com departamento incompatível com o papel.', n; END IF;

    SELECT count(*) INTO n FROM iam_role_assignments a JOIN iam_roles r ON r.id = a.role_id
     WHERE r.tenant_id <> a.tenant_id;
    IF n > 0 THEN RAISE EXCEPTION 'V015: % atribuição(ões) com papel de outro tenant.', n; END IF;
END $$;

-- 1) Papéis: só papéis de sistema; todos os 5 em todo tenant
ALTER TABLE iam_roles
    ADD CONSTRAINT ck_iam_roles_system_name
        CHECK (name IN ('COLLABORATOR','LEADER','MANAGER','EXECUTIVE','TENANT_ADMIN')),
    ADD CONSTRAINT uq_iam_roles_tenant_id UNIQUE (tenant_id, id);

INSERT INTO iam_roles (id, tenant_id, name, created_at, updated_at)
SELECT gen_random_uuid(), t.id, k.name, now(), now()
  FROM iam_tenants t
 CROSS JOIN (VALUES ('COLLABORATOR'),('LEADER'),('MANAGER'),('EXECUTIVE'),('TENANT_ADMIN')) AS k(name)
 WHERE NOT EXISTS (SELECT 1 FROM iam_roles r WHERE r.tenant_id = t.id AND r.name = k.name);

-- 2) Matriz nova (substitui a anterior; não há papéis customizados)
DELETE FROM iam_role_permissions;

WITH ladder(resource, action, min_rank) AS (VALUES
        ('CHAT','CREATE',1),('CHAT','READ',1),('KNOWLEDGE_DOCUMENT','READ',1),
        ('USAGE','READ',1),('MODEL','READ',1),
        ('USER','READ',2),('GOVERNANCE_SCORE','READ',2),
        ('USER','MANAGE',3),('INVITE','MANAGE',3),('ROLE','READ',3),('DEPARTMENT','READ',3),
        ('PROJECT','MANAGE',3),('AI_TOOL','READ',3),
        ('TENANT','READ',4)),
     ranks(name, r) AS (VALUES ('COLLABORATOR',1),('LEADER',2),('MANAGER',3),('EXECUTIVE',4))
INSERT INTO iam_role_permissions (role_id, resource, action)
SELECT ro.id, l.resource, l.action
  FROM iam_roles ro
  JOIN ranks k ON k.name = ro.name
  JOIN ladder l ON l.min_rank <= k.r;

INSERT INTO iam_role_permissions (role_id, resource, action)
SELECT ro.id, p.resource, p.action
  FROM iam_roles ro
 CROSS JOIN (VALUES
        ('TENANT','READ'),('TENANT','MANAGE'),('ROLE','READ'),('ROLE','MANAGE'),
        ('USER','READ'),('USER','MANAGE'),('INVITE','READ'),('INVITE','MANAGE'),
        ('DEPARTMENT','READ'),('DEPARTMENT','MANAGE'),('AI_TOOL','READ'),('AI_TOOL','MANAGE'),
        ('MODEL','READ'),('MODEL','MANAGE'),
        ('KNOWLEDGE_DOCUMENT','READ'),('KNOWLEDGE_DOCUMENT','MANAGE'),
        ('LICENSE','MANAGE'),('TRAINING','MANAGE'),('USAGE','READ'),('GOVERNANCE_SCORE','READ')
     ) AS p(resource, action)
 WHERE ro.name = 'TENANT_ADMIN';

-- 3) Concessões: escopo, origem, autoria, revogação
ALTER TABLE iam_role_assignments
    ADD COLUMN scope_type VARCHAR(16),
    ADD COLUMN origin     VARCHAR(16),
    ADD COLUMN granted_by UUID,
    ADD COLUMN granted_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by UUID;

UPDATE iam_role_assignments a
   SET scope_type = CASE r.name WHEN 'COLLABORATOR' THEN 'SELF'
                                WHEN 'LEADER'       THEN 'DEPARTMENT'
                                WHEN 'MANAGER'      THEN 'DEPARTMENT'
                                ELSE 'TENANT' END,
       origin = 'MIGRATION',
       granted_at = now()
  FROM iam_roles r
 WHERE r.id = a.role_id;

-- Admins legados (as 9 permissões do V004) viram TENANT_ADMIN
INSERT INTO iam_role_assignments
       (id, tenant_id, user_id, role_id, department_id, scope_type, origin, granted_at)
SELECT gen_random_uuid(), u.tenant_id, u.id, r.id, NULL, 'TENANT', 'MIGRATION', now()
  FROM app_user u
  JOIN iam_roles r ON r.tenant_id = u.tenant_id AND r.name = 'TENANT_ADMIN'
 WHERE u.id IN (SELECT user_id FROM user_permission
                 GROUP BY user_id HAVING count(DISTINCT permission) = 9);

ALTER TABLE iam_role_assignments
    ALTER COLUMN scope_type SET NOT NULL,
    ALTER COLUMN origin     SET NOT NULL,
    ALTER COLUMN granted_at SET NOT NULL,
    ALTER COLUMN granted_at SET DEFAULT now(),
    ADD CONSTRAINT ck_iam_ra_scope  CHECK (scope_type IN ('SELF','DEPARTMENT','TENANT')),
    ADD CONSTRAINT ck_iam_ra_origin CHECK (origin IN ('BOOTSTRAP','INVITE','DIRECT','MIGRATION')),
    ADD CONSTRAINT ck_iam_ra_dept   CHECK ((scope_type = 'DEPARTMENT') = (department_id IS NOT NULL)),
    ADD CONSTRAINT ck_iam_ra_revoke CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)),
    ADD CONSTRAINT fk_iam_ra_role_same_tenant FOREIGN KEY (tenant_id, role_id)
        REFERENCES iam_roles (tenant_id, id),
    ADD CONSTRAINT fk_iam_ra_granted_by FOREIGN KEY (granted_by) REFERENCES app_user (id),
    ADD CONSTRAINT fk_iam_ra_revoked_by FOREIGN KEY (revoked_by) REFERENCES app_user (id);

CREATE UNIQUE INDEX uq_iam_ra_active ON iam_role_assignments
    (tenant_id, user_id, role_id, COALESCE(department_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE revoked_at IS NULL;

-- 4) Convites carregam a concessão; token só como hash; pendentes antigos revogados
ALTER TABLE iam_invites
    ADD COLUMN role_id    UUID,
    ADD COLUMN invited_by UUID;

UPDATE iam_invites SET status = 'REVOKED' WHERE status = 'PENDING';
UPDATE iam_invites SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');

ALTER TABLE iam_invites RENAME COLUMN token TO token_hash;
ALTER TABLE iam_invites RENAME CONSTRAINT uq_iam_invites_token TO uq_iam_invites_token_hash;
ALTER TABLE iam_invites
    ADD CONSTRAINT fk_iam_invites_role_same_tenant FOREIGN KEY (tenant_id, role_id)
        REFERENCES iam_roles (tenant_id, id),
    ADD CONSTRAINT fk_iam_invites_invited_by FOREIGN KEY (invited_by) REFERENCES app_user (id),
    ADD CONSTRAINT ck_iam_invites_pending_role CHECK (status <> 'PENDING' OR role_id IS NOT NULL);
```

Observações para o implementador:

- `user_permission` **não** é removida na V015. O `DROP` vai numa migration futura (V021+, release
  seguinte — decisão 7).
- O `PermissionMatrix`/`SystemRole` em Java e o SQL acima devem bater. Há um IT que compara os dois
  (AZ18c).
- `hierarchy_level` do convite continua obrigatório como rótulo. Se o pedido não trouxer rótulo, ele
  é derivado do papel (nome igual a um `HierarchyLevel`). Para `TENANT_ADMIN` sem rótulo, usar
  `EXECUTIVE`.

## 6. Endpoints — gate e alvo

`TO` = `TENANT_ONLY` (padrão); `BT` = `BY_TARGET`, com checagem fina obrigatória no caso de uso.

| Método e rota | Permissão | Targeting | Alvo e regra no caso de uso | Tarefa |
|---|---|---|---|---|
| POST /api/v1/tenants | público | — | cria tenant, papéis, admin e concessão BOOTSTRAP | A4 |
| GET /api/v1/tenants/me | autenticado | — | sem mudança | — |
| PUT /api/v1/tenants/me (+ identidade-visual) | TENANT:MANAGE | TO | — | A2 |
| GET/POST /api/v1/departamentos | DEPARTMENT:READ / MANAGE | READ=BT, MANAGE=TO | lista filtrada por `visibilityFor(DEPARTMENT:READ)` (ids do escopo; TenantWide = todos) | A3 |
| GET /api/v1/departamentos/{id} | DEPARTMENT:READ | BT | `isAllowed(DepartmentTarget(id))`, senão 403 | A3 |
| PUT/DELETE /api/v1/departamentos/{id} | DEPARTMENT:MANAGE | TO | — | A2 |
| /api/v1/ai-gateway/providers, /models, /models/{id}/status, /departments/{id}/model-policy | AI_TOOL:MANAGE | TO | — | A2 |
| GET /api/v1/ai-gateway/models/permitido | autenticado | — | sem mudança | — |
| GET /api/v1/convites | INVITE:MANAGE | BT | só convites cujo papel e depto o ator pode conceder | A5 |
| POST /api/v1/convites | INVITE:MANAGE | BT | `podeConceder(ator, roleKey, departmentId)`, senão 403 | A5 |
| POST /api/v1/convites/aceitar | público | — | revalida `podeConceder(invitedBy, …)`, senão 409 | A5 |
| POST /api/v1/convites/{id}/reenviar, DELETE /api/v1/convites/{id} | INVITE:MANAGE | BT | ator pode conceder o papel do convite | A5 |
| POST /api/v1/usuarios | — | — | **removido** (404/405) | A7 |
| GET /api/v1/usuarios | USER:READ | BT | `visibilityFor(USER:READ)`: Departments ⇒ `departmentId IN ids OR id = self`; SelfOnly ⇒ só o ator | A7 |
| PATCH /api/v1/usuarios/{id} (depto) | USER:MANAGE | BT | `domina(ator, alvo)` e o novo depto coberto pelo ator | A7 |
| PATCH /api/v1/usuarios/{id}/status | USER:MANAGE | BT | `domina`; último admin → 409 | A7 |
| POST /api/v1/usuarios/{id}/nivel | USER:MANAGE | BT | `domina`; o rótulo não tem efeito de autorização | A7 |
| GET /api/v1/usuarios/me/permissoes | autenticado | — | concessões efetivas (§7.3) | A8 |
| GET /api/v1/usuarios/{id}/permissoes | USER:READ | BT | `isAllowed(OwnedTarget(id, deptAtual))` | A7 |
| GET /api/v1/papeis | ROLE:READ | BT | lista os 5 papéis com escopo e posto; marca `concedivel` para o ator | A6 |
| POST /api/v1/usuarios/{id}/concessoes | ROLE:MANAGE | TO | só TENANT_ADMIN; ator ≠ alvo; origem DIRECT | A6 |
| DELETE /api/v1/usuarios/{id}/concessoes/{concessaoId} | ROLE:MANAGE | TO | ator ≠ alvo; último admin → 409; revogação lógica | A6 |
| GET /api/v1/observability/metrics/usage, /costs | USAGE:READ | BT | `visibilityFor(USAGE:READ)`; `departmentId` pedido fora do escopo → 403 | A3 |
| GET /api/v1/observability/usage | USAGE:READ | BT | idem; `userId` pedido fora do escopo → 403 | A3 |

Novos endpoints de autorização: **3** (`GET /papeis`, `POST` e `DELETE` de concessões), dentro do
teto do plano.

## 7. Payloads novos ou alterados

### 7.1 `POST /api/v1/tenants`

```json
{ "slug":"acme", "name":"ACME", "emailDomain":"acme.com",
  "adminName":"Ana", "adminEmail":"ana@acme.com", "adminPassword":"..." }
```

- `adminEmail` deve pertencer a `emailDomain` (400 se não pertencer).
- Senha com no mínimo 8 caracteres; não há outra política de senha.
- Resposta 201: `TenantResponse` (sem dados de senha).
- Tudo na mesma transação (`@Transactional` no `RegisterTenant`): tenant, papéis, usuário e
  concessão `TENANT_ADMIN`/TENANT com origem `BOOTSTRAP`.

### 7.2 `POST /api/v1/convites`

```json
{ "email":"x@acme.com", "roleKey":"LEADER", "departmentId":"<uuid|null>", "hierarchyLevel":"LEADER" }
```

- `departmentId` é obrigatório se o papel for DEPARTMENT.
- `hierarchyLevel` é opcional (§5).
- A resposta 201 inclui `token` **uma única vez**: só o hash fica no banco (SHA-256 hex) e o token
  nunca vai para log.
- `GET /convites` nunca devolve token.

### 7.3 `GET /api/v1/usuarios/me/permissoes` e `/{id}/permissoes`

```json
{ "userId":"…",
  "concessoes":[{"id":"…","papel":"MANAGER","escopo":"DEPARTMENT","departmentId":"…","origem":"INVITE"}],
  "permissoes":["USER:READ","USAGE:READ", "..."] }
```

- `permissoes` é a união das permissões das concessões ativas, sem escopo.
- O front usa esse endpoint para exibir menus. Isso não é autorização.

### 7.4 `POST /api/v1/usuarios/{id}/concessoes`

- Request: `{ "roleKey":"MANAGER", "departmentId":"…" }`.
- Respostas:
  - 201: concessão criada;
  - 409: concessão ativa idêntica já existe;
  - 403: ator = alvo.

### 7.5 `GET /api/v1/papeis`

```json
[{ "key":"LEADER", "escopo":"DEPARTMENT", "posto":2, "concedivel":true }]
```

## 8. Convenção de erros (ADR-36, item 10)

| Situação | Status |
|---|---|
| Recurso de outro tenant | 404 |
| Sem permissão no gate; fora do escopo; filtro fora do escopo; ator = alvo; não domina | 403 |
| Último admin; convidante perdeu autoridade no aceite; concessão duplicada | 409 |
| Token de convite inválido, expirado ou reutilizado | 400 (como hoje) |

## 9. Fixtures de teste (obrigatório, V19)

- Criar `src/test/java/br/com/qsoft/governance/support/TestTenants.java` (helper de IT).
- Ele só usa casos de uso de produção:
  - `RegisterTenant` (com admin) → admin TENANT_ADMIN;
  - `CreateInvite` + `AcceptInvite` → demais usuários com o papel e depto pedidos;
  - `AssignRole` → quando precisar de 2 concessões.
- Proibido `RoleAssignment.grant(...)`/`new RoleAssignment(...)` e `roleAssignmentRepository.save`
  em ITs, exceto em `RolePersistenceIT`/`RoleAssignmentPersistenceTest` (persistência pura).
- Substituir nos 9 ITs listados em `VERIFICACAO_FASE0.md` (V19).

## 10. ArchUnit (S3-A8)

1. Nenhuma classe em `..iam.application.rbac..`, `..iam.domain.rbac..`,
   `..iam.infrastructure.web.PermissionInterceptor` ou no pacote `..observability..` depende de
   `iam.domain.user.HierarchyLevel`.
2. Construtor e `grant` de `RoleAssignment` só são chamados a partir de `..iam.application..` e do
   próprio `..iam.domain.rbac..` (o mapper JPA usa o construtor canônico → permitir
   `..iam.infrastructure.persistence..`).
3. Nenhuma classe fora do `iam` referencia `iam.api.model.Permission` (já garantido pela remoção).

## 11. Matriz de testes

| ID | Cenário | Esperado | Tipo | Tarefa |
|---|---|---|---|---|
| AZ01 | Registrar tenant com `demo-data=false`; registrante loga e faz `PUT /tenants/me` | 200; concessão TENANT_ADMIN com origem BOOTSTRAP | IT | A4 |
| AZ02 | Admin convida COLLABORATOR; aceite | concessão SELF, `granted_by` = admin, origem INVITE | IT | A5 |
| AZ03 | COLLABORATOR lê uso e lista usuários | uso 200 só com os próprios dados; `/usuarios` 403 | IT | A5 (`OnboardingFlowIT`) |
| AZ04 | Usuário com todas as concessões revogadas | 403 em todo endpoint anotado | IT | A6 |
| AZ05 | Admin muda rótulo de LEADER para EXECUTIVE | visibilidade continua a do depto | IT | A7 |
| AZ06 | Gestor altera o próprio rótulo, depto, status ou concessões | 403 | IT | A7 |
| AZ07 | MANAGER(A) convida MANAGER, EXECUTIVE, TENANT_ADMIN ou qualquer papel no depto B; e LEADER em A | 403 nos primeiros; 201 no último | IT | A5 |
| AZ08 | MANAGER(A) lê usuário de B; lista usuários | 403; lista sem B | IT | A7 |
| AZ09 | Token do tenant A com IDs do B; concessão com papel de outro tenant | 404; FK composta rejeita | IT + IT de banco | A1, A7 |
| AZ10 | Concessão SELF com MANAGE sobre alvo de depto | negado | unit | A2 |
| AZ11 | Revogar concessão e repetir a requisição com o mesmo JWT | 403 | IT | A6 |
| AZ12 | EXECUTIVE e TENANT_ADMIN tentam ler chat de outro | negado (teto SELF / sem CHAT) | unit (A2) + IT (D2) | A2, D2 |
| AZ13 | Token de convite reutilizado, adulterado ou expirado | 400; token ausente dos logs; só hash no banco | IT | A5 |
| AZ14 | Gestor move subordinado para depto fora do escopo | 403 | IT | A7 |
| AZ15 | Revogar ou desativar o último TENANT_ADMIN | 409 | IT | A6, A7 |
| AZ16 | MANAGER(A) desativa ou altera um admin lotado em A | 403 | IT | A7 |
| AZ17 | Convidante perde a concessão antes do aceite | aceite 409; nenhum usuário criado | IT | A5 |
| AZ18a | V015 sobre banco com admin legado e seed demo | admin vira TENANT_ADMIN; EXECUTIVE com escopo TENANT | IT Flyway | A1 |
| AZ18b | V015 com permissões parciais ou MANAGER sem depto | migration aborta | IT Flyway | A1 |
| AZ18c | Permissões persistidas × `SystemRole` | idênticas para os 5 papéis | IT | A1 |
| AZ19 | Endpoint `TENANT_ONLY` com concessão DEPARTMENT; regra ArchUnit | 403; nenhum import de `HierarchyLevel` em autorização | IT + ArchUnit | A2, A8 |
| AZ20 | COLLABORATOR em `GET /observability/usage` com `userId` de outro | 403; sem filtro → só os próprios registros | IT | A3 |
| DB01 | MANAGER(A) filtra o dashboard pelo depto B | 403 | IT | A3 |
| DB02 | Usuário com MANAGER(A) e MANAGER(B) | vê A ∪ B | IT | A3 |
| DB03 | Gestor muda de depto (por admin) mantendo a concessão em A | continua vendo A, não o novo | IT | A3 |
| DB04 | LEADER sem concessão departamental (só rótulo) | vê só os próprios dados (nunca o tenant) | IT | A3 |

As **ITs de Flyway (AZ18)** usam Testcontainers e o Flyway programático:
`Flyway.configure().target("14")`, depois inserção dos dados legados, depois `migrate()` até 15.
