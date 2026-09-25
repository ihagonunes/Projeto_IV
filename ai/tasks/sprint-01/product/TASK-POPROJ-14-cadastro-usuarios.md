# TASK-POPROJ-14 — Cadastro de usuários e convites com papéis definidos

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-14
- **Jira SM:** SPI-1
- **Story Points:** 8
- **Status no consolidado:** To-Do
- **Frente / Épico:** Core & Acessos (EPIC-01)
- **Tipo:** Backend / IAM

## Objetivo

Permitir o cadastro de usuários da organização com nível hierárquico definido.

## Escopo

- Modelar User no módulo IAM.
- Associar usuário ao tenant e ao Department conforme a arquitetura.
- Permitir atribuição de um dos níveis hierárquicos padronizados.
- Preparar o domínio para ativação/desativação do usuário.

## Fora de escopo

- Reproduzir toda a hierarquia real de cada empresa.
- Transformar ADMIN em nível hierárquico.
- Definir fluxo de convite por e-mail sem requisito específico.

## Critérios de aceitação

- [ ] Usuário é criado dentro do tenant correto.
- [ ] Usuário recebe nível válido: COLLABORATOR, LEADER, MANAGER ou EXECUTIVE.
- [ ] ADMIN permanece conjunto de permissões e não HierarchyLevel.
- [ ] Usuário não pode ser criado/alterado fora do escopo autorizado.
- [ ] Persistência/migrations e testes relevantes passam.

## Módulo proprietário / área

`iam` com consultas organizacionais do `department` quando necessário

## Dependências

- POPROJ-13 — Tenant existente.
- POPROJ-51 — isolamento multi-tenant.

## Contexto a carregar

- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ai/adr/ADR-04-iam.md`
- `ai/adr/ADR-05-user-area-ownership.md`

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/iam/`
- `src/main/java/br/com/qsoft/governance/department/api/`
- `src/main/resources/db/migration/`
- `src/test/java/`

## Checks

- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`

## Observações

- O consolidado menciona 'convites', mas não detalha canal, token, expiração ou fluxo. Não inventar esse mecanismo sem requisito adicional.

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
