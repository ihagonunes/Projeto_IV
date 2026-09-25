# Como rodar o projeto (How to run)

Guia prático para rodar a Plataforma de Governança de IA (Monólito Modular + BFF Node) localmente.

> Pré-requisitos: JDK 21, Maven 3.9+, Git, Node.js 18+ (para o frontend), e um dos: **PostgreSQL 18 local** ou **Docker** (para o modo reproduzível e para os testes de integração).

---

## Visão geral dos componentes

| Componente | Tecnologia | Porta padrão | Papel |
|---|---|---|---|
| **Backend** | Spring Boot (Java 21) | `8080` | API REST `/api/v1`, JWT, Flyway, PostgreSQL |
| **Frontend (BFF)** | Node + Express + EJS | `3000` | Serve as views e faz proxy de `/api/*` para o backend |
| **Banco** | PostgreSQL | `5432` | Persistência; Flyway aplica as migrations |

### Estado atual (baseline implementada)

| Módulo | Situação |
|---|---|
| `iam` | ✅ Tenant, User, autenticação (JWT), RBAC por papéis, convites, status e identidade visual |
| `department` | ✅ CRUD de departamentos + orçamento + contratos públicos |
| `aigateway` | 🚧 Em andamento (catálogo Provider/Model; roteamento em construção) |
| `sharedkernel` / `platform` | ✅ Tipos transversais e configuração |
| `project`, `chat`, `observability`, `engagement`, `governancescore`, `agentpolicies` | ⏳ Planejados |

Migrations Flyway: **`V001`–`V011`**. Testes: **149** unitários/arquitetura + **64** de integração.

---

## 1. Configurar o ambiente (uma vez)

Copie o arquivo de exemplo e ajuste os valores:

```bash
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env
```

Variáveis úteis (`.env`):

| Variável | Uso | Obs. |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Banco do backend | Ajuste ao seu PostgreSQL |
| `SERVER_PORT` | Porta do backend | padrão `8080` |
| `API_PROXY_TARGET` | Alvo do proxy `/api/*` do frontend | padrão `http://localhost:8080` |
| `QSOFT_TENANT_SLUG` | Slug do tenant injetado no login | opcional (o login também deriva o tenant do domínio do e-mail) |
| `JWT_SECRET` | Segredo de assinatura/validação do JWT | **OBRIGATÓRIO (mín. 32 chars); sem ele o app não sobe** |
| `APP_BOOTSTRAP_DEMO_DATA` | Habilita o seed de demonstração | padrão `false`; use `true` apenas em dev |
| `APP_BOOTSTRAP_DEMO_PASSWORD` | Senha do admin demo (seed) | necessária quando o seed está habilitado |

---

## 2. Rodar o backend

As migrations Flyway (`V001`–`V011`) são aplicadas automaticamente na inicialização (`ddl-auto=validate`).

### Modo A — desenvolvimento local (PostgreSQL instalado)

1. Crie o banco configurado em `DB_NAME`.
2. Rode:

```bash
# Windows (PowerShell)
./scripts/run-local.ps1

# Linux/macOS
./scripts/run-local.sh
```

Ou rode a classe `GovernanceApplication` pela IDE (importando o projeto Maven).

### Modo B — backend em container (Docker)

O repositório fornece um **`Dockerfile`** (build multi-stage: Maven → JRE 21). **Não há `docker-compose.yml`.**

```bash
docker build -t qsoft-governance .
docker run --rm -p 8080:8080 \
  -e DB_HOST=host.docker.internal -e DB_PORT=5432 \
  -e DB_NAME=qsoft_governance -e DB_USER=qsoft -e DB_PASSWORD=change_me_local \
  -e JWT_SECRET=<segredo-com-32-ou-mais-caracteres> \
  qsoft-governance
```

> Os **testes de integração não usam esse container**: eles sobem um **PostgreSQL 18.6 via Testcontainers** em `mvn verify -Pintegration`.

---

## 3. Rodar o frontend (BFF Node)

O frontend lê o `.env` da raiz (via `dotenv`), então os valores de `API_PROXY_TARGET` e `QSOFT_TENANT_SLUG` já são considerados.

```bash
npm install
npm start         # padrão: http://localhost:3000
```

- Acesse **http://localhost:3000/login** (o login chama o backend via proxy).
- Porta: defina `PORT` no `.env` se quiser trocar a 3000.

---

## 4. Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## 5. Exercitar a API

Endpoints principais (prefixo `/api/v1`):

- **Criar tenant** (público/onboarding):
  ```bash
  curl -X POST http://localhost:8080/api/v1/tenants \
    -H "Content-Type: application/json" \
    -d '{"slug":"qsoft","name":"QSOFT","emailDomain":"qsoft.com"}'
  ```
  > `emailDomain` é **obrigatório e único globalmente** (SPI-54): identifica o domínio corporativo do tenant e é o que torna determinística a resolução do tenant no login.
- **Login** (emite JWT Bearer). `tenantSlug` é opcional: se ausente, o tenant é derivado do domínio do e-mail:
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@qsoft.com","password":"<senha>"}'
  ```
- **Criar usuário** (endpoint **protegido** — exige JWT + permissão `USER:MANAGE`; o tenant vem do token):
  ```bash
  curl -X POST http://localhost:8080/api/v1/usuarios \
    -H "Authorization: Bearer <token>" \
    -H "Content-Type: application/json" \
    -d '{"email":"ana@qsoft.com","name":"Ana","hierarchyLevel":"MANAGER","password":"secret123"}'
  ```
- **Endpoints protegidos**: envie o token retornado:
  ```bash
  curl http://localhost:8080/api/v1/... \
    -H "Authorization: Bearer <token>"
  ```
- **Departamentos** (protegido — `DEPARTMENT:READ` / `DEPARTMENT:MANAGE`):
  ```bash
  curl -X POST http://localhost:8080/api/v1/departamentos \
    -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
    -d '{"name":"Marketing","description":"Time de marketing","monthlyBudget":"1500.00"}'
  ```
- **Convites** (`INVITE:MANAGE`): `POST /api/v1/convites`, `GET /api/v1/convites`,
  `POST /api/v1/convites/{id}/reenviar`, `DELETE /api/v1/convites/{id}`; o aceite é público
  (`POST /api/v1/convites/aceitar`).
- **Tenant do contexto**: `GET`/`PUT /api/v1/tenants/me` e `GET`/`PUT /api/v1/tenants/me/identidade-visual`.
- **Usuários**: `GET /api/v1/usuarios`, `PATCH /api/v1/usuarios/{id}`,
  `PATCH /api/v1/usuarios/{id}/status`, `POST /api/v1/usuarios/{id}/nivel`,
  `GET /api/v1/usuarios/me/permissoes`.

> **Swagger/OpenAPI:** com a aplicação no ar, a superfície completa da API fica em
> **http://localhost:8080/swagger-ui.html** (controlado por `OPENAPI_ENABLED`, `true` em dev).

> **Dados de demonstração (opcional):** habilite `APP_BOOTSTRAP_DEMO_DATA=true` e `APP_BOOTSTRAP_DEMO_PASSWORD=<senha>` para o backend criar, na inicialização, o tenant `qsoft` e o admin `admin@qsoft.com`. Sem isso, o seed **não** roda (padrão seguro).
>
> **Importante:** o `POST /api/v1/usuarios` é **protegido** (JWT + `USER:MANAGE`) e o `tenantId` vem do token — nunca do corpo. Para o primeiro usuário, use o seed de demonstração ou crie via migração/admin.

---

## 6. Testes

| Comando | O que roda | Exige Docker? |
|---|---|---|
| `mvn test` | Testes unitários + ArchUnit (ciclo rápido) | Não |
| `mvn verify -Pintegration` | Testes de integração com PostgreSQL real (Testcontainers) | Sim |
| `./scripts/check --changed` | Requeridos + `mvn test` (imprime arquivos alterados) | Não |
| `./scripts/check` | Compilação + unit/architecture | Não |
| `./scripts/check-integration` | `mvn verify -Pintegration` | Sim |

---

## 7. Problemas comuns

- **`node_modules` ausente** (erro ao rodar o front): `npm install`.
- **App não sobe com erro de `JWT_SECRET`**: defina `JWT_SECRET` no `.env` com **32+ caracteres** (é obrigatório).
- **Login retorna 400**: informe um `email` válido; o `tenantSlug` é opcional (derivado do domínio do e-mail).
- **`POST /api/v1/usuarios` retorna 401/403**: o endpoint é protegido — envie `Authorization: Bearer <token>` de um usuário com a permissão `USER:MANAGE`.
- **Login falha / não há usuário**: habilite o seed demo (`APP_BOOTSTRAP_DEMO_DATA=true` + `APP_BOOTSTRAP_DEMO_PASSWORD`) ou crie o usuário pela API protegida.
- **Schema vazio / `missing table [app_user]`**: confirme que a dependência `spring-boot-starter-flyway` está no `pom.xml` e `spring.flyway.enabled=true` (Flyway aplica as migrations na inicialização).
- **ITs falham por falta de Docker**: inicie o Docker Desktop/Engine antes de `mvn verify -Pintegration`.
- **Portas em conflito (8080/3000/5432)**: altere `SERVER_PORT`, `PORT` e `DB_PORT` no `.env`.

---

## Referência

- `README.md` — visão geral e baseline técnica.
- `docs/development/ENVIRONMENT.md` — requisitos e modos de execução.
- `docs/development/BOOTSTRAP.md` — bootstrap técnico e primeira feature sugerida.
- `docs/lessonslearned&Decisions.md` — decisões da implementação (auditar o que foi feito).
