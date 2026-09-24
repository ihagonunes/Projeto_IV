# TASK-POPROJ-57 — Configuração do repositório GitHub e ambiente Docker

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-57
- **Story Points:** 2
- **Status no consolidado:** To-Do
- **Frente / Épico:** Infraestrutura / Fundação
- **Tipo:** Fundação técnica

## Objetivo

Disponibilizar o repositório e o ambiente reproduzível da aplicação sem tornar Docker obrigatório para o desenvolvimento local.

## Escopo

- Manter o projeto como um único Maven/Spring Boot.
- Disponibilizar Dockerfile do backend e Compose com backend + PostgreSQL.
- Permitir desenvolvimento local com Spring Boot + PostgreSQL local.
- Versionar somente configurações seguras e exemplos de variáveis.

## Fora de escopo

- Criar um container por módulo do Monólito Modular.
- Adicionar H2.
- Introduzir Kubernetes, broker, Redis ou infraestrutura não prevista.

## Critérios de aceitação

- [ ] O repositório pode ser clonado e a estrutura principal do starter é preservada.
- [ ] `docker compose config` valida a configuração.
- [ ] O Compose define backend e PostgreSQL com healthcheck do banco.
- [ ] Existe `.env.example` sem segredos reais.
- [ ] README diferencia execução local e Docker.
- [ ] Nenhum módulo de negócio é transformado em serviço/container independente.

## Módulo proprietário / área

`platform` / infraestrutura do repositório

## Dependências

- Nenhuma dependência explícita registrada para esta task.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/dependencies.md`
- `docs/development/ENVIRONMENT.md`

## Arquivos prováveis

- `Dockerfile`
- `compose.yaml`
- `.env.example`
- `.gitignore`
- `README.md`
- `.github/workflows/ci.yml`

## Checks

- `./scripts/check --changed`
- `docker compose config`

## Observações

- Docker é estratégia de execução/empacotamento; PostgreSQL continua sendo o único banco suportado.

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
