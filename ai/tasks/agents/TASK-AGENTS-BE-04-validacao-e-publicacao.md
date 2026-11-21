# TASK-AGENTS-BE-04 — Validação server-side e publicação

## Metadados

- **Jira:** SPI-180.
- **Depende de:** BE-03.
- **Módulo:** `agents`.
- **Migration:** nenhuma nova; usa V027.
- **Não depende de:** ADR-45.

## Objetivo

Tornar o servidor autoritativo sobre o grafo e permitir publicar uma versão
válida e imutável.

## Fora de escopo

- execução;
- chamadas a provider;
- frontend;
- novos endpoints de catálogo.

## Critérios de aceitação

- [ ] Implementar validação server-side equivalente à UI:
  - [ ] uma `task` e um `result`;
  - [ ] `task` sem entrada e `result` sem saída;
  - [ ] sem ciclo, autoloop, aresta duplicada ou nó órfão;
  - [ ] todos os nós alcançáveis a partir de `task`;
  - [ ] `result` alcançável;
  - [ ] `merge` com ≥2 entradas e ≤1 saída;
  - [ ] todos os ramos abertos consolidados por `merge`;
  - [ ] `agentKey` existente, ativo/visível e resolvido com override;
  - [ ] `kind` conhecido;
  - [ ] coordenadas finitas em `[0,1]`;
  - [ ] limites de nós/arestas/JSON/schemaVersion.
- [ ] Erros com código estável e caminho do nó/aresta; sem stack trace.
- [ ] `POST /api/v1/agentes/fluxos/{id}/validar`:
  - [ ] `AGENT:MANAGE`;
  - [ ] valida sem persistir;
  - [ ] retorna `valid`, `needsMerge` e erros.
- [ ] `POST /api/v1/agentes/fluxos/{id}/publicar`:
  - [ ] `AGENT:MANAGE`;
  - [ ] exige versão `VALID`;
  - [ ] cria/pina versão publicada imutável;
  - [ ] não altera versões antigas;
  - [ ] conflito de versão → 409/412.
- [ ] Grafo enviado já normalizado por `consolidate` é aceito; o servidor não
  confia na validação do cliente e revalida tudo.
- [ ] Testes:
  - [ ] cada regra negativa;
  - [ ] fluxo linear válido;
  - [ ] fan-out + merge;
  - [ ] ramo direto no resultado;
  - [ ] nó órfão;
  - [ ] resultado ausente;
  - [ ] merge com uma entrada;
  - [ ] `agentKey` inexistente/inativo;
  - [ ] payload acima do limite;
  - [ ] publicação de versão inválida → 422/400;
  - [ ] versão publicada imutável;
  - [ ] Tenant A ≠ B.
- [ ] `ai/contracts.md` e `docs/development/AGENTES_PIPELINE_IMPLEMENTACAO.md`
  atualizados se o contrato mudar.

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/agents/domain/**`
- `src/main/java/br/com/qsoft/governance/agents/application/**`
- `src/main/java/br/com/qsoft/governance/agents/infrastructure/web/**`
- `src/test/java/br/com/qsoft/governance/agents/**`

## Checks

- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js` — não aplicável se o front não for tocado.

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
