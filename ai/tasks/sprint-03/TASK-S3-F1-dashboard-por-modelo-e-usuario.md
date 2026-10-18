# TASK-S3-F1 — Dashboard: custo por modelo e por usuário, filtros de modelo e de usuário

## Metadados
- **Onda:** F (dashboard) · **Depende de:** A3
- **Jira:**
  - SPI-97, SPI-98, SPI-99, SPI-100 (manter);
  - SPI-113, SPI-114, SPI-115, SPI-116 (fechar como duplicatas).
- **Módulo proprietário:** `observability`

## Decisão de contrato (sem endpoint novo)
Estender `GET /api/v1/observability/metrics/costs` com:
- `groupBy=DEPARTMENT|MODEL|USER` (padrão `DEPARTMENT`, que é o comportamento atual);
- filtros opcionais `modelId` e `userId`.

Estender `GET /metrics/usage` com os filtros `modelId` e `userId`.

## Critérios de aceitação
- [ ] Agrupamento por `model_id` e `user_id`, com o mesmo recorte de `VisibilityScope` da A3.
- [ ] `userId` fora do escopo → 403.
- [ ] `groupBy=USER` com visibilidade SelfOnly devolve só o próprio usuário.
- [ ] A resposta de grupo é `{ chave, rotulo?, requisicoes, tokensEntrada, tokensSaida, custoEstimado }`.
  - O rótulo do modelo vem do `aigateway.api` (`AIToolCatalogQuery` ou uma nova consulta de nome de
    modelo). O `observability` **não** pode depender do `aigateway` pela allowlist: devolver só IDs
    e deixar o front resolver os nomes pelo catálogo.
  - Registrar essa escolha no relatório.
- [ ] ITs:
  - [ ] agrupamentos;
  - [ ] filtros combinados (período + depto + modelo + usuário);
  - [ ] RBAC (MANAGER(A) por usuário só vê usuários de A);
  - [ ] isolamento de tenant.
- [ ] Front do dashboard: seletores de modelo, de usuário (só quando a visibilidade não é SelfOnly)
      e de agrupamento.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
