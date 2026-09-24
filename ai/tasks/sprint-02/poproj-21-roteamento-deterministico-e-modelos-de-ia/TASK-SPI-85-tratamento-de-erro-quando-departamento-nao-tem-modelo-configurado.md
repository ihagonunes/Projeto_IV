# TASK-SPI-85 — Tratamento de erro quando departamento não tem modelo configurado

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-85
- **História PO:** POPROJ-21
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Roteamento Determinístico e Modelos de IA

## Fonte original do consolidado
Mensagem informativa instruindo o usuário a contatar o administrador e geração de log de auditoria.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Tratar ausência de modelo permitido/default sem fallback.

### Requisitos reconciliados
- [ ] Erro de domínio claro.
- [ ] Sem modelo aleatório/alternativo.
- [ ] Orientar contato com admin.
- [ ] Log/auditoria sem conteúdo privado.

### Motivo
Routing determinístico sem fallback.

## Área proprietária
`aigateway + department`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-07`
- `ADR-08`
- `ADR-09`
- `ADR-11`
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
