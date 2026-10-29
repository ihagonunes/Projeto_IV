# Como rodar o projeto (How to run)

Guia prático para rodar a Plataforma de Governança de IA (Monólito Modular + BFF Node) localmente.

> **Instalação atual da equipe:** usar o [setup automático](BANCO_E_IMPORTACAO_HISTORICA.md#instalação-da-equipe--comece-aqui)
> para banco vazio, migrations, importação do snapshot e contas por perfil.
> Começar por `npm run setup:local -- --init-env .env.team.local`.
> Os [resultados de validação](VALIDACAO_E_PENDENCIAS.md) registram a versão mais recente.

> Pré-requisitos: JDK 21, Maven 3.9+, Git, Node.js 24, e um dos: **PostgreSQL 18 local** ou **Docker** (para o modo reproduzível e para os testes de integração).

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
| `department` | ✅ CRUD de departamentos, orçamento mensal padrão, política de modelos e contratos públicos |
| `aigateway` | ✅ Catálogo Provider/Model, preços e roteamento determinístico. 🚧 Provider real e completion: Sprint 3 (TASK-S3-C1) |
| `observability` | ✅ Registro de uso, consulta, dashboard (uso e custo) e histórico QSOFT (schema `historical`) |
| `sharedkernel` / `platform` | ✅ Tipos transversais e configuração |
| `chat`, `agentpolicies` | 🚧 Planejados para a Sprint 3 (`ai/tasks/sprint-03/`) |
| `project`, `engagement`, `governancescore` | ⏳ Planejados |

Migrations Flyway atuais: **`V001`–`V021`**. Os testes e pendências atualizados
estão em [Validação e pendências](VALIDACAO_E_PENDENCIAS.md).

> `POST /api/v1/tenants` recebe os dados do primeiro administrador e cria sua
> concessão TENANT_ADMIN. O setup usa esse fluxo; o seed de demonstração é opcional.

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

As migrations Flyway (`V001`–`V014`) são aplicadas automaticamente na inicialização (`ddl-auto=validate`).

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

### Modo B — Docker Compose (PostgreSQL + backend)

O repositório tem um **`compose.yaml`** (PostgreSQL 18.6 + backend) e um **`Dockerfile`** (build
multi-stage: Maven → JRE 21).

```bash
cp .env.example .env      # ajuste DB_* e JWT_SECRET
docker compose up --build
```

> O serviço `backend` repassa `JWT_SECRET` (obrigatório, mínimo de 32 caracteres) e, opcionalmente,
> `APP_BOOTSTRAP_DEMO_DATA` e `APP_BOOTSTRAP_DEMO_PASSWORD`. Sem `JWT_SECRET`, o
> `docker compose` falha antes de subir o backend.

#### Backend em container avulso

```bash
docker build -t qsoft-governance .
docker run --rm -p 8080:8080 \
  -e DB_HOST=host.docker.internal -e DB_PORT=5432 \
  -e DB_NAME=qsoft_governance -e DB_USER=qsoft -e DB_PASSWORD=change_me_local \
  -e JWT_SECRET=<segredo-com-32-ou-mais-caracteres> \
  qsoft-governance
```

> Os **testes de integração não usam esse container**: eles sobem um **PostgreSQL 18.6 via Testcontainers** em `mvn verify -Pintegration`.

### Provider real (OPENAI_COMPATIBLE)

O provider real é opcional e usa apenas configuração; não há SDK nem dependência nova.

1. Defina a chave no ambiente do **backend** (nunca no repositório). O nome da variável é a
   `credentialRef` cadastrada no provider:

   ```bash
   # exemplo; não comite este valor
   export QSOFT_LLM_API_KEY=<chave-do-fornecedor>
   ```

   Para Ollama local, cadastre o provider com `credentialRef` nulo: nenhum header
   `Authorization` é enviado.

2. Cadastre o provider (`POST /api/v1/ai-gateway/providers`) com `kind=OPENAI_COMPATIBLE`,
   `baseUrl` do fornecedor e `credentialRef=QSOFT_LLM_API_KEY`.
3. Cadastre o modelo (`POST /api/v1/ai-gateway/models`) com `externalName` do fornecedor e os
   dois preços por 1.000 tokens. Modelos `AVAILABLE` exigem preço; zero explícito é aceito.
4. Configure a política do departamento (`PUT /api/v1/ai-gateway/departments/{id}/model-policy`)
   e use o chat.

A chave existe apenas no ambiente do backend; ela não é devolvida em DTO, log ou banco. Variável
ausente → `CREDENTIAL_MISSING` (502).

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
- **Adicionar usuário: use o convite.** `POST /api/v1/usuarios` não aceita senha, e o usuário
  criado por ele nunca consegue logar. Esse endpoint será removido na Sprint 3 (decisão 3,
  TASK-S3-A7). O caminho correto:
  ```bash
  # 1) o admin cria o convite (INVITE:MANAGE); a resposta traz o token uma única vez
  curl -X POST http://localhost:8080/api/v1/convites \
    -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
    -d '{"email":"ana@qsoft.com","hierarchyLevel":"MANAGER"}'
  # 2) a pessoa convidada aceita e define a senha (público)
  curl -X POST http://localhost:8080/api/v1/convites/aceitar \
    -H "Content-Type: application/json" \
    -d '{"token":"<token-do-convite>","name":"Ana","password":"<senha>"}'
  ```
  > **Limitação atual (N01):** o usuário convidado ainda **não** recebe papel. Ele consegue logar,
  > mas recebe 403 nos endpoints protegidos até a TASK-S3-A5 (convite com concessão).
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
> **Primeiro acesso:** o setup automático ou `POST /api/v1/tenants` criam o tenant
> e o primeiro administrador. Consulte os emails gerados no relatório local do setup.

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
- **Usuário convidado recebe 403 em tudo**: limitação conhecida (N01). O convite ainda não atribui papel; a correção é a TASK-S3-A5.
- **Login falha / não há usuário**: habilite o seed demo (`APP_BOOTSTRAP_DEMO_DATA=true` + `APP_BOOTSTRAP_DEMO_PASSWORD`). Um tenant criado por `POST /tenants` ainda não tem usuário (N01).
- **Schema vazio / `missing table [app_user]`**: confirme que a dependência `spring-boot-starter-flyway` está no `pom.xml` e `spring.flyway.enabled=true` (Flyway aplica as migrations na inicialização).
- **ITs falham por falta de Docker**: inicie o Docker Desktop/Engine antes de `mvn verify -Pintegration`.
- **Portas em conflito (8080/3000/5432)**: altere `SERVER_PORT`, `PORT` e `DB_PORT` no `.env`.

---

## Referência

- `README.md` — visão geral e baseline técnica.
- `docs/development/ENVIRONMENT.md` — requisitos e modos de execução.
- `docs/development/BOOTSTRAP.md` — bootstrap técnico do starter (desatualizado; ver `docs/review/AUDITORIA_DOCUMENTACAO.md`, item 6).
- `docs/development/CONTRATO_API_FRONT_BACK.md` — contrato REST atual entre front e backend.
- `ai/tasks/sprint-03/INDEX.md` — plano da Sprint 3 em execução.
- `docs/lessonslearned&Decisions.md` — decisões da implementação (auditar o que foi feito).
