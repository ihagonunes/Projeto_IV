# Revisão e Debug — Relatório consolidado

> **Nota de status (2026-10-18).** Este relatório é anterior à `057fbea`.
> - A linha de base "126 + 52" foi substituída por **717 unit + 83 IT** (`VERIFICACAO_FASE0.md`
>   §1B).
> - Da tabela "Frontend × Backend", só continua quebrado `identidade-visual.js`, que chama
>   `/tenant/identidade-visual` (singular). Os demais itens foram corrigidos. Situação atual:
>   `docs/development/CONTRATO_API_FRONT_BACK.md` §2.
> - Os achados A1 (`hierarchyLevel` × RBAC) e M4 (senha em `POST /usuarios`) foram decididos: ADR-36,
>   ADR-37 e a decisão 3 (`POST /usuarios` será removido).

**Agentes:** `debug` (validação + fluxos alterados + endpoints do frontend) e `reviewer` (bugs/regressões/testes).
**Método:** somente-leitura. Nenhuma alteração foi feita.
**Baseline:** `mvn -B test` = 126 ✅ · `mvn -B verify -Pintegration` = 52 ITs ✅ (Docker OK na máquina).
**Nenhuma falha crítica de segurança nova** encontrada no backend; o que existe é dívida arquitetural e contratos quebrados entre frontend e backend.

---

## 1. O que está CORRETO e validado

- Multi-tenancy em duas camadas (predicado explícito `tenant_id` + `@Filter` Hibernate).
- RBAC granular (ADR-33), `PolicyEvaluator`, `PermissionInterceptor` global.
- JWT HS256 stateless, claims estáveis, segredo obrigatório ≥32 chars no boot.
- SPI-54: domínio único globalmente (V007), resolução por domínio cadastrado com fallback (ADR-32 emendado), cobertura de unit e IT.
- SPI-61: guarda de auto-desativação.
- SPI-66: expiração de token com 401 uniforme, sem refresh.
- SPI-67: rate limiting em memória com chave IP+email normalizada, reset on success, 429 genérico.
- Cross-tenant rejeitado em `UpdateUser`, `ChangeUserStatus`, listagem (cobertura IT).
- Tenant ACTIVE/INACTIVE bloqueia login e requests protegidos (cobertura IT).
- Handlers não vazam stack/SQL; logs sem tokens/senhas.
- ArchUnit OK.

---

## 2. Achados — prioridade para ação

### 🔴 Frontend × Backend — endpoints que **não funcionam** (C1, A1, C2 — agente `debug`)

| Chamada no frontend | Verbo/Path backend | Estado | Efeito |
|---|---|---|---|
| `Aceitar convite` (página aceite-convite) | `POST /api/v1/auth/aceitar-convite` | **não existe** | Backend expõe `/api/v1/convites/aceitar` — **todo aceite de convite falha (404)** |
| `Lista/POST/revoga convites` (admin) | `GET/POST/DELETE /api/v1/admin/convites` | **não existe** | Backend usa `/api/v1/convites` (e não há GET de convites) |
| `Editar usuário (papel)` (admin) | `PUT /api/v1/usuarios/{id}/permissao` | **não existe** | Backend: `PATCH /api/v1/usuarios/{id}` ou `POST /api/v1/usuarios/{id}/nivel` |
| `Atualizar usuário` (BFF) | `PUT /api/v1/usuarios/{id}` | **mismatch de verbo** | Backend só tem `PATCH` — **405 Method Not Allowed** |
| `Perfil da empresa` (GET/PUT) | `GET/PUT /api/v1/tenant/me` | **mismatch de path** | Backend usa `/api/v1/tenants/me` (plural) |
| `Identidade visual` (tela) | `GET/PUT /api/v1/tenant/identidade-visual` | **não existe** | Tela grava em `localStorage` via `try/catch` — **nada persiste no backend** |
| `Salvar edição da empresa` | `PUT /tenant/me` com `{nome, cnpj, setor, porte, fusoHorario, idioma}` | **mismatch de path + payload silencioso** | Backend só aceita `name`; `cnpj/setor/...` são descartados em silêncio |
| `POST /api/v1/tenants` (onboarding) | exige `emailDomain` não-vazio | backend OK; frontend OK | consistente com SPI-54 |

**Origem:** o frontend foi escrito contra um contrato mais antigo (pré-SPI-47/59/62/63) e várias páginas "funcionam" hoje só porque caem em `try/catch` local que escreve em `localStorage` (ex.: `admin-convites.js:194`, `editar-empresa.js:142-144`, `identidade-visual.js:131-138`). A UI passa impressão de salvar sem persistir.

### 🟠 Decisões arquiteturais pendentes

- **A1 do reviewer** — `User.hierarchyLevel` (mutável) × modelo RBAC real: o endpoint muda `hierarchyLevel`, mas `PolicyEvaluator` ignora — só lê `iam_role_assignments`. O `user_permission` legado (V004) continua sendo gravado por `ProvisionUser` e ninguém o lê → código morto + contrato enganoso (frontend "muda privilégio" mas não muda). **Decisão:** (a) manter `hierarchyLevel` como rótulo e documentar; (b) propagar para `iam_role_assignments` ao mudar.
- **M4 do reviewer** — `POST /api/v1/usuarios` aceita `password` no body: o frontend define senha inicial por fora (deveria ser via fluxo de convite). **Decisão:** remover do payload (o convite é o caminho legítimo) ou abrir ADR descrevendo o trade-off.
- **C2 do debug** — `PUT /tenant/me` aceita `{nome, cnpj, setor, porte, fusoHorario, idioma}` no frontend; o DTO só tem `name`. Silencioso. Se for para SPI-53 (identidade visual) ainda falta muito; se for apenas "name", alinhar a UI.

### 🟠 Risco futuro de segurança/correctness

- **A2 do debug** — `UserRepositoryAdapter.findById` sem `TenantContext` **é seguro hoje** (único caller é `TenantContextFilter`, que compensa), mas é uma **armadilha**: qualquer caller novo sem popular o contexto vaza usuários cross-tenant. **Recomendado:** refatorar para `findById(UserId)` que sempre filtra, e expor `findByIdRaw` explícito para o filter.
- **A3 do debug** — JSON de erro escrito manualmente (`PermissionInterceptor` e `SecurityBaselineConfig`) sem escape robusto. Frágil se uma negação devolver input do cliente. **Recomendado:** usar `ObjectMapper`.
- **M3 do reviewer** — `LIKE` em filtro de usuário não escapa `%`/`_` (parâmetros vinculados, mas semântica de busca pode surpreender). **Recomendado:** `ESCAPE '\\'` + substituir curingas.
- **M5 do reviewer** — `LIKE` em `UserRepositoryAdapter:108-116` idem.

### 🟡 Resiliência/contrato

- **M1 do reviewer** — `LoginAttemptLimiter.purgeIfNeeded` é O(n) sob contenção quando o mapa passa de 10k chaves não expiradas. Aceitável para MVP (já documentado como limitação), mas amortizar com `@Scheduled` ou contador.
- **M2 do reviewer** — resposta **429 não inclui `Retry-After`**. Cliente legítimo continua martelando; não-conforme com RFC 6585.
- **A2 do reviewer** — drift: `@Column(unique=true)` em `TenantJpaEntity.email_domain` sugere que o JPA gera constraint, mas V007 só cria UNIQUE INDEX. `slug` também tem redundância. **Recomendado:** remover `unique=true` das anotações (Flyway é fonte).
- **B4 do reviewer** — `ResendInvite` e `RevokeInvite` **não são `@Transactional`**: se o segundo `save` falhar, o estado fica inconsistente (admin acha que reenviou mas só revogou). **Recomendado:** anotar como `@Transactional`.
- **B1 do debug** — `DemoDataSeeder` não captura `TenantDomainAlreadyExistsException`: se outro tenant já tiver `qsoft.com`, o boot explode.

### 🔵 Testes — lacunas e falso-positivo

- **T1 do reviewer (falso-positivo)** — `UserManagementIT.shouldUpdateUserHierarchyLevelAndDepartment` testa a mudança de `hierarchyLevel` e o teste passa por motivos cosméticos. Como o sistema usa RBAC (papéis), mudar `hierarchyLevel` **não move permissões reais**. O teste passa mesmo se o endpoint parasse de salvar o campo. **Recomendado:** validar o efeito de autorização (via `/me/permissoes`) ou restringir o teste a `departmentId`.
- **T2 do reviewer (flakiness latente)** — `LoginRateLimitIT.shouldResetCounterOnSuccessfulLogin` depende de o limiter estar zerado no início. Hoje funciona, mas qualquer novo teste que use o mesmo e-mail contamina. **Recomendado:** expor `LoginAttemptLimiter` e chamar `reset(...)` no `@BeforeEach`.
- **T3 do reviewer** — bordas de paginação não testadas (page negativo → silenciosamente 0; `PageResult.totalPages()` não exercitado com `size` inválido).
- **T4 do reviewer** — IT não cobre cross-tenant em filtros de busca `?name=…`/`?email=…`, nem "reenvio de convite já expirado" no HTTP.
- **T9 do reviewer** — domínios IDN (Unicode) não testados (`münchen.de`); o `toLowerCase(Locale.ROOT)` pode divergir do collate do PG.

---

## 3. Sugestão de roteiro

Eu recomendo **fechar o contrato frontend×backend primeiro** (C1, A1, C2) antes de seguir para SPI-53/65/67. Sem isso, a SPI-53 (identidade visual) e as próximas telas de usuário/convite vão nascer quebradas da mesma forma.

Ordem que proponho:

1. **Alinhar frontend×backend** — escolher um lado e fazer o match (a maioria é `PUT→PATCH` e `tenant→tenants`; identidade-visual e admin/convites exigem decidir se ajustamos a UI ou estendemos o backend).
2. **Resolver `hierarchyLevel` × RBAC** (decisão arquitetural; ADR pequeno).
3. **`password` em `POST /usuarios`** (decisão de segurança).
4. **Refator `findById` (A2 debug)** — baixo risco, alto ganho de segurança.
5. **Pequenas correções de resiliência/teste** (`@Transactional` em invites, `Retry-After`, escapes de LIKE, falsificar o teste de hierarchyLevel, `DemoDataSeeder`, etc.).
6. **Só então**: SPI-53, SPI-65 (após decisão), SPI-67 já feito.

---

## Como prosseguir

Me diga:

- **(a)** Quer que eu **comece a alinhar o frontend×backend** agora (escolha: ajustar a UI ou estender o backend)? Posso propor um patch mínimo para os casos triviais (verbo `PUT→PATCH`, `tenant→tenants`) e deixar a decisão de identidade visual/admin/convites para você.
- **(b)** Quer priorizar a decisão arquitetural de **`hierarchyLevel` × RBAC** (ADR pequeno) antes?
- **(c)** Quer só **fazer o debug manual** (subir a aplicação, exercitar fluxos) com base nesse roteiro antes de qualquer alteração?

Sem a sua escolha, eu não toco em código.
