# TASK-POPROJ-63 — Indicadores e Análise Exploratória (20%)

## Metadados

- **Sprint:** 1
- **Jira PO:** POPROJ-63
- **Story Points:** 16
- **Status no consolidado:** In Progress
- **Frente / Épico:** Deliverable 1 / Estatística Multivariada
- **Tipo:** Entregável acadêmico

## Objetivo

Definir indicadores organizacionais, preparar os dados e realizar a análise exploratória do Deliverable 1.

## Escopo

- Definir indicadores organizacionais.
- Preparar a base de dados.
- Executar EDA coerente com os indicadores.

## Fora de escopo

- Implementar modelos multivariados do Deliverable 2.
- Misturar dados operacionais novos com históricos sem separação explícita.

## Critérios de aceitação

- [ ] Indicadores possuem definição e variáveis de origem identificáveis.
- [ ] Tratamentos de qualidade/ausência de dados são registrados.
- [ ] EDA apresenta distribuições, relações e limitações relevantes.
- [ ] Fonte histórica permanece identificada como dado analítico separado.

## Módulo proprietário / área

Dados / Analytics

## Dependências

- Nenhuma dependência explícita registrada para esta task.

## Contexto a carregar

- `ai/project.md`
- `ai/architecture.md`
- `ai/adr/ADR-26-operational-and-historical-analytics-separation.md`

## Arquivos prováveis

- `docs/`
- `dados/notebooks (se o repositório adotar essa pasta)`

## Checks

- `Revisar artefato contra o enunciado da disciplina`
- `./scripts/check --changed (somente se houver mudança no repositório)`

## Observações

- Não ampliar o escopo além do que o consolidado da Sprint 1 suporta.

## Fonte

`consolidado.md` — Consolidação das Tasks e Histórias da Sprint 1.
