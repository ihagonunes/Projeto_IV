# TASK-S4-P1 — Provider real ponta a ponta e custo sem preço

## Metadados
- **Onda:** P · **Depende de:** H1
- **Jira:** **SPI-138** (redefinida: o adaptador já existe; falta provar ponta a ponta e corrigir o
  custo); parte da **SPI-133** (valores batem com o `UsageRecord`)
- **Módulo:** `aigateway`
- **Spec:** `SPEC-08-provider-real-e-catalogo.md` §1–§3, §6 (PR01–PR05)
- **Bloqueio parcial:** decisão **S4-D1** (provider da demo). O código e os testes não dependem
  dela.

## Critérios de aceitação
- [ ] Regra de preço da SPEC-08 §2:
  - [ ] validação ao colocar AVAILABLE (400 `MODEL_PRICE_REQUIRED`);
  - [ ] em runtime, preço nulo → `INCONCLUSIVE` + `PRICE_MISSING`;
  - [ ] `Model.estimateCost` exige os dois preços.
- [ ] `credentialRef` nulo → sem header `Authorization` (Ollama). Variável ausente →
      `CREDENTIAL_MISSING` (502).
- [ ] Testes PR01–PR05. O servidor HTTP de teste é o `com.sun.net.httpserver.HttpServer` do JDK,
      sem dependência nova.
- [ ] `.env.example`: `QSOFT_LLM_API_KEY=` vazio, com comentário.
- [ ] `HOW_TO_RUN.md`: seção "Provider real" (passos da SPEC-08 §3, sem segredo).
- [ ] Emenda ao ADR-38 (seção "Emenda (Sprint 4) — preço obrigatório"), sem alterar o Status.
- [ ] Nenhum log com prompt, resposta, header ou segredo (appender de teste).
- [ ] Quando o S4-D1 for decidido, registrar no relatório o roteiro manual executado com o provider
      real: modelo, latência e recibo. **Nunca** commitar a chave.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
