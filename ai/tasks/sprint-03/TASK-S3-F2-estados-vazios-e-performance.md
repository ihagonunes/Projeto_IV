# TASK-S3-F2 — Estados vazios ("ausente ≠ zero") e performance das agregações

## Metadados
- **Onda:** F · **Depende de:** F1 (C1 para "inconclusivo")
- **Jira:** SPI-95, SPI-101 (SPI-117 fechar como duplicata)
- **Área:** `observability` + front do dashboard

## Critérios de aceitação
- [ ] Período ou filtro sem dados → o front mostra "Sem dados no período", nunca 0.
- [ ] Custo com tentativas inconclusivas → mostrar "custo conhecido" + contador de inconclusivos.
      O front obtém o contador pela RF047 (C3); sem a C3, o contador fica oculto.
- [ ] Mensagens próprias para 401 (redireciona ao login), 403, 5xx e timeout. Indicador de
      carregamento durante as requisições.
- [ ] Performance (SPI-101):
  - [ ] rodar `EXPLAIN (ANALYZE, BUFFERS)` das consultas de `/metrics/*` e `/orcamentos` sobre a
        massa histórica (V014) ou sobre uma massa sintética de pelo menos 100 mil linhas em IT
        local;
  - [ ] registrar os planos no relatório;
  - [ ] **só** criar índice se houver evidência de *seq scan* relevante. Nesse caso, a migration
        `V020__observability_metrics_indexes.sql` (ex.:
        `(tenant_id, model_id, created_at DESC)`) precisa de aprovação do responsável antes, por
        estourar o teto de migrations.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
- `npm start`, com navegação manual
