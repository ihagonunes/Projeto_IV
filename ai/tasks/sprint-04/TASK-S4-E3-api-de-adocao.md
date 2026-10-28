# TASK-S4-E3 — API de adoção (ativos, inativos, taxa, licenças ociosas)

## Metadados
- **Onda:** E · **Ordem:** 3 · **Depende de:** E2
- **Jira:** **SPI-143** (rota `/api/v1/engajamento/adocao` em vez de `/api/v1/adoption/metrics` —
  decisão S4-D10; denominador = elegíveis — decisão S4-D3)
- **Módulo:** `engagement`
- **Spec:** `SPEC-06` §5 (recorte) e §5.1

## Critérios de aceitação
- [ ] `GET /api/v1/engajamento/adocao?fonte=&ate=&departmentId=`:
  - [ ] USAGE:READ, BY_TARGET;
  - [ ] recorte por `visibilityFor`;
  - [ ] `departmentId` fora → 403;
  - [ ] `fonte` inválida → 400;
  - [ ] `ate` futuro → 400.
- [ ] Resposta conforme SPEC-06 §5.1, com `porDepartamento` agrupado pela **lotação IAM** e
      `cobertura`.
- [ ] Valores operacionais conferidos contra o `UsageRecord` em IT: o critério da SPI-143 é "batem
      com a contagem de usuários com registros".
- [ ] Testes:
  - [ ] EN03;
  - [ ] EN07 (adoção);
  - [ ] EN11.
- [ ] `ai/contracts.md` documenta a rota.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
