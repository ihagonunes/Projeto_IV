# Mapa de contexto

O agente não deve carregar toda a documentação por padrão.

| Nível | Arquivo | Quando carregar |
|---|---|---|
| 0 | `AGENTS.md` | Sempre. |
| 1 | `ai/project.md` | Produto, escopo, requisitos. |
| 1 | `ai/architecture.md` | Módulos, ownership, dependências, persistência. |
| 1 | `ai/contracts.md` | Ports, eventos, DTOs e API. |
| 1 | `ai/dependencies.md` | Biblioteca, framework, versão ou ferramenta. |
| 1 | `ai/testing.md` | Estratégia de testes, ArchUnit, PostgreSQL. |
| 1 | `ai/decisions.md` | Para localizar ADR relacionado. |
| 2 | `ai/adr/ADR-XX-*.md` | Somente ADR relacionado à tarefa. |
| 2 | `ai/epics/EPIC-XXX-*.md` | Somente epic relacionado. |
| 2 | `ai/tasks/TASK-*.md` | Quando existir task packet. |
| 3 | Código/testes | Somente caminhos apontados pelo plano/diff. |
| 4 | `docs/reference/*` | Apenas quando o contexto condensado não bastar. |

## Economia de contexto

1. Comece pelo pedido + `AGENTS.md`.
2. Escolha no máximo os arquivos de nível 1 necessários.
3. Use `ai/decisions.md` como índice; não leia 31 ADRs por padrão.
4. Leia código apenas do módulo proprietário e dependências diretas.
5. Em review, comece por `git diff --stat` e `git diff`.
6. Documentos DOCX em `docs/reference/` são fonte longa e não devem ser ingeridos em toda tarefa.
