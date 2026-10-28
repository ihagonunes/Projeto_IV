# TASK-S4-P2 — Catálogo de modelos: GET da política, edição do modelo e tela administrativa

## Metadados
- **Onda:** P · **Depende de:** P1 (regra de preço)
- **Jira:** **SPI-87**
- **Módulos:** `aigateway` (2 endpoints) e o front (`step-ia.js`, `modules/ia`)
- **Spec:** `SPEC-08` §4, §5, §6 (PR06–PR08)

## Critérios de aceitação
- [ ] `GET /api/v1/ai-gateway/departments/{id}/model-policy` (AI_TOOL:MANAGE, TO). Depto de outro
      tenant → 404.
- [ ] `PUT /api/v1/ai-gateway/models/{id}` (AI_TOOL:MANAGE, TO):
  - [ ] edita nome, `externalName` e preços;
  - [ ] valida preço negativo (400) e nome duplicado (409);
  - [ ] não mexe em status nem provider.
- [ ] O front deixa de chamar `/api/v1/ia/*` e passa a usar `/api/v1/ai-gateway/*` (SPEC-08 §5).
- [ ] A tela:
  - [ ] lista os modelos com provider, status, preços e departamentos autorizados;
  - [ ] edita status, preços e nome externo;
  - [ ] edita a política por departamento, respeitando o padrão ∈ permitidos;
  - [ ] não tem fallback para `localStorage` nem sucesso simulado.
- [ ] O segredo nunca é exibido; `credentialRef` aparece como "nome da variável".
- [ ] Testes PR06–PR08.
- [ ] `docs/development/CONTRATO_API_FRONT_BACK.md` §2.3 atualizado.

## Restrição
Não tocar no componente de chat.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js`
