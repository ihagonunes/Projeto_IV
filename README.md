# QSOFT AI Governance Platform

Starter repository da Plataforma de Governança de IA do Projeto Integrador IV.

## Baseline técnica

- Java 21
- Spring Boot 4.1.1
- Maven
- PostgreSQL 18
- Flyway
- Spring Data JPA
- Spring Security
- JWT/OAuth2 Resource Server primitives
- Bean Validation
- Actuator
- ArchUnit
- JUnit 5
- Testcontainers 2.0.5
- Docker Compose opcional

A aplicação é um **Monólito Modular**: existe um único build/deploy do backend, com fronteiras lógicas fortes entre os módulos Java.

## Como começar

### Modo 1 — desenvolvimento local leve

Use Spring Boot + PostgreSQL instalado localmente.

1. Copie `.env.example` para `.env`.
2. Ajuste usuário/senha do PostgreSQL.
3. Crie o banco configurado em `DB_NAME`.
4. Execute:

Windows PowerShell:

```powershell
./scripts/run-local.ps1
```

Linux/macOS:

```bash
./scripts/run-local.sh
```

Ou configure as variáveis de ambiente na IDE e execute `GovernanceApplication`.

### Modo 2 — ambiente reproduzível com Docker

```bash
cp .env.example .env
docker compose up --build
```

Serviços:

- backend: `http://localhost:8080`
- health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`

> Docker é opcional para desenvolvimento. PostgreSQL é o único SGBD suportado.

## Checks

Durante o desenvolvimento:

```bash
./scripts/check --changed
```

Antes de concluir uma tarefa:

```bash
./scripts/check
```

Testes de integração com PostgreSQL real via Testcontainers:

```bash
./scripts/check-integration
```

Os testes de integração exigem Docker disponível, mas **não rodam no ciclo rápido padrão**.

## Desenvolvimento assistido por IA

Leia primeiro:

1. `AGENTS.md`
2. `ai/context-map.md`
3. somente o contexto apontado pela tarefa

Fluxo recomendado no OpenCode:

```text
/plan → /implement → /test → /review
```

A documentação longa de referência fica em `docs/reference/`. A fonte de contexto operacional para agentes fica em `ai/`, deliberadamente mais curta e progressiva.

## Estrutura

```text
.
├── AGENTS.md
├── ai/
│   ├── context-map.md
│   ├── project.md
│   ├── architecture.md
│   ├── contracts.md
│   ├── dependencies.md
│   ├── testing.md
│   ├── decisions.md
│   ├── adr/
│   ├── epics/
│   └── tasks/
├── docs/
│   ├── development/
│   └── reference/
├── .opencode/
├── .vscode/
├── src/
│   ├── main/java/br/com/qsoft/governance/
│   ├── main/resources/
│   └── test/java/br/com/qsoft/governance/
├── compose.yaml
├── Dockerfile
└── pom.xml
```

## Regra de ouro

Se código, Jira, C4 e documentação divergirem, a arquitetura consolidada em `docs/reference/` e os ADRs em `ai/adr/` devem ser reconciliados antes de avançar a implementação.
