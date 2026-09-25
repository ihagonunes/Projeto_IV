# TASK-POPROJ-67 — Estrutura backend (20%)

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-67
- **Story Points:** 9
- **Status no consolidado:** To-Do
- **Frente / Épico:** Deliverable 1 / Programação Backend
- **Tipo:** Entregável acadêmico

## Objetivo

Estabelecer a estrutura inicial da aplicação e demonstrar a arquitetura do backend.

## Escopo

- Usar o starter Java/Spring Boot.
- Manter a estrutura física do Monólito Modular.
- Garantir build, ArchUnit e configuração de PostgreSQL.

## Fora de escopo

- Implementar frontend nesta task.
- Migrar o backend para Node.
- Separar módulos em microservices.

## Critérios de aceitação

- [ ] Aplicação sobe a partir de `GovernanceApplication`.
- [ ] Packages principais correspondem aos módulos da arquitetura.
- [ ] ArchUnit protege fronteiras.
- [ ] PostgreSQL/Flyway estão configurados.
- [ ] `mvn test` passa no ambiente de desenvolvimento.

## Módulo proprietário / área

Backend / Fundação

## Dependências

- Nenhuma dependência explícita registrada para esta task.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/dependencies.md`
- `docs/development/BOOTSTRAP.md`

## Arquivos prováveis

- `pom.xml`
- `src/main/`
- `src/test/`
- `README.md`

## Checks

- `Revisar artefato contra o enunciado da disciplina`
- `./scripts/check --changed (somente se houver mudança no repositório)`

## Observações

- Não ampliar o escopo além do que o consolidado da Sprint 1 suporta.

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
