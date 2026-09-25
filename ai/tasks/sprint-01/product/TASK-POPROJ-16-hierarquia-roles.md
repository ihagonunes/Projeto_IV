# TASK-POPROJ-16 — Definição e alteração de papéis (roles) em 4 níveis padronizados

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-16
- **Jira SM:** SPI-1
- **Story Points:** 8
- **Status no consolidado:** To-Do
- **Frente / Épico:** Core & Acessos (EPIC-01)
- **Tipo:** Backend / IAM

## Objetivo

Implementar a hierarquia organizacional padronizada usada pelas regras de autorização.

## Escopo

- Suportar COLLABORATOR, LEADER, MANAGER e EXECUTIVE.
- Permitir alteração controlada do nível de um usuário.
- Manter ADMIN separado como permission set.
- Centralizar interpretação de hierarquia no IAM.

## Fora de escopo

- Criar regras de hierarquia duplicadas em outros módulos.
- Adicionar níveis adicionais sem decisão arquitetural.
- Usar frontend como mecanismo de autorização.

## Critérios de aceitação

- [ ] Os quatro níveis hierárquicos estão modelados de forma explícita.
- [ ] ADMIN não aparece no enum/VO de HierarchyLevel.
- [ ] Alteração de nível exige autorização apropriada.
- [ ] Outros módulos consultam IAM em vez de reinterpretar hierarquia.
- [ ] Testes unitários e de autorização passam.

## Módulo proprietário / área

`iam`

## Dependências

- POPROJ-14 — cadastro de usuários.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ai/adr/ADR-04-iam.md`

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/iam/`
- `src/test/java/br/com/qsoft/governance/`

## Checks

- `./scripts/check --changed`
- `./scripts/check`

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
