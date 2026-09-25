# TASK-SPI-72 — Cálculo de custo estimado por requisição

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-72
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Fórmula: (input_tokens/1000 * preco_in) + (output_tokens/1000 * preco_out).

## Versão reconciliada com a arquitetura

### Objetivo técnico
Calcular estimatedCost no AI Gateway uma única vez.

### Requisitos reconciliados
- [ ] Usar tokens e preço do modelo/provedor.
- [ ] Enviar custo em AIRequestCompleted.
- [ ] Observability apenas persiste/agrega.
- [ ] Não recalcular no Observability.

### Motivo
ADR-13.

## Área proprietária
`observability + aigateway`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-13`
- `ADR-26`
- `ADR-29`

## Regras transversais
- Cross-module somente via `module.api.*`.
- Sem associação JPA/FK SQL cross-module.
- TenantId vem do contexto autenticado.
- Persistência muda somente com Flyway.
- PostgreSQL é o único banco suportado.
- Frontend nunca substitui autorização backend.
- Mudança estrutural exige ADR.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
