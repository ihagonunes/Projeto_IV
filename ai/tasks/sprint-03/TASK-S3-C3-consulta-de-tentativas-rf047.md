# TASK-S3-C3 — Consulta de tentativas (RF047)

## Metadados
- **Onda:** C · **Depende de:** C1, A2
- **Jira:** **SPI-171** (RF047)
- **Módulo proprietário:** `aigateway`
- **Spec:** `SPEC-03` §4 (primeira linha), §5 (TR10)

## Critérios de aceitação
- [ ] `GET /api/v1/ai-gateway/tentativas` (USAGE:READ, BT):
  - [ ] paginado (padrão 20, máximo 100);
  - [ ] filtros `de`, `ate`, `status`, `deliveryStatus`, `userId`, `departmentId`;
  - [ ] recorte por `visibilityFor`; filtro fora do escopo → 403;
  - [ ] devolve só metadados.
- [ ] Tentativas DENIED aparecem (decisão 14, recomendação; se o responsável decidir o contrário,
      filtrar).
- [ ] ITs:
  - [ ] TR10;
  - [ ] TR07 visível pelo endpoint (`deliveryStatus=DEAD`).
- [ ] **Não** ligar `tentativasInconclusivas` (B2) nem o contador de inconclusivos do dashboard
      (F2) a esta tabela. Pela allowlist, o `observability` não pode depender do `aigateway`.
  - O front obtém o contador pela RF047 (`status=INCONCLUSIVE` e o total paginado).
  - Mudar a allowlist exige ADR.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
