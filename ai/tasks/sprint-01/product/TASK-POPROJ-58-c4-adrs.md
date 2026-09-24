# TASK-POPROJ-58 — Documentação da Arquitetura (C4 Model) e ADRs

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-58
- **Story Points:** 3
- **Status no consolidado:** To-Do
- **Frente / Épico:** Arquitetura / Fundação
- **Tipo:** Documentação arquitetural

## Objetivo

Garantir que a arquitetura do projeto esteja documentada e rastreável antes da implementação do core.

## Escopo

- Manter o C4 como representação da arquitetura vigente.
- Manter os ADRs formais e o índice de decisões.
- Garantir consistência entre arquitetura funcional, C4, `ai/architecture.md` e `ai/adr/`.

## Fora de escopo

- Redesenhar a arquitetura sem uma nova decisão formal.
- Usar C4 como substituto dos ADRs.
- Duplicar toda a documentação longa dentro de cada task.

## Critérios de aceitação

- [ ] Os documentos finais de arquitetura e C4 estão em `docs/reference/`.
- [ ] Os ADRs formais estão individualizados em `ai/adr/`.
- [ ] `ai/decisions.md` funciona como índice dos ADRs.
- [ ] C4 e arquitetura descrevem o mesmo Monólito Modular, PostgreSQL e fronteiras de módulos.

## Módulo proprietário / área

Documentação / arquitetura transversal

## Dependências

- POPROJ-57 — repositório disponível.

## Contexto a carregar

- `AGENTS.md`
- `ai/context-map.md`
- `ai/architecture.md`
- `ai/decisions.md`

## Arquivos prováveis

- `docs/reference/`
- `ai/adr/`
- `ai/decisions.md`
- `ai/architecture.md`

## Checks

- `./scripts/check --changed`

## Observações

- Esta task é predominantemente documental; não exige criação de código de domínio.

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
