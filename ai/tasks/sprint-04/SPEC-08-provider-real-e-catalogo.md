# SPEC-08 — Provider real, custo sem preço e tela do catálogo (SPI-138, SPI-87)

Base: ADR-09, ADR-10, ADR-12, ADR-13 e ADR-38.
Tarefas: S4-P1 (backend do provider e custo), S4-P2 (catálogo: backend e tela).

## 1. Estado atual (verificado em 27/10/2026)

- `aigateway/infrastructure/provider/` já tem:
  - `OpenAiCompatibleProviderClient` (`RestClient`, sem SDK);
  - `StubProviderClient`;
  - `EnvCredentialResolver`;
  - `ProviderClientRegistryAdapter`.
- O provider concreto da demo **não** foi escolhido (decisão 11 → S4-D1).
- Bug de custo: `Model.estimateCost` trata preço nulo como zero (`Model.java:147-157`). Uma
  completion num modelo sem preço gera custo `KNOWN = 0`, o que parece gratuidade.
- O front do catálogo (`public/js/pages/onboarding/step-ia.js`) chama `/api/v1/ia/provedores`,
  `/ia/modelos` e `/ia/modelos/{id}/status`, que **não existem**. Os endpoints corretos são
  `/api/v1/ai-gateway/*`.
- Não existe `GET` da política de modelos do departamento: só o
  `PUT /api/v1/ai-gateway/departments/{id}/model-policy`.

## 2. Regra de preço (decisão S4-D12, recomendada)

1. **Validação de catálogo:**
   - colocar um modelo em `AVAILABLE` (criação ou `PATCH status`) exige `inputPricePer1k` e
     `outputPricePer1k` **não nulos**; zero explícito é permitido;
   - sem preço → 400 `MODEL_PRICE_REQUIRED`.
   - Modelos já `AVAILABLE` sem preço continuam como estão (sem migration de dados); valem as
     regras de runtime abaixo.
2. **Defesa em runtime:**
   - `CompleteAIRequest` com preço nulo e uso `Known` → `cost_status = INCONCLUSIVE` (sem custo e
     sem `UsageRecord`), `error_code = PRICE_MISSING`;
   - `Model.estimateCost` passa a exigir os dois preços (lança se algum for nulo); o chamador
     decide.
3. **Emenda ao ADR-38:** registrar a regra numa seção "Emenda (Sprint 4)".

## 3. Provider real da demo (S4-D1)

Enquanto o S4-D1 não for decidido, a tarefa entrega tudo com um provider HTTP **simulado** nos
testes. Depois, só configuração: nenhum código muda por fornecedor.

| Passo | Detalhe |
|---|---|
| Variável de ambiente | O responsável cria (fora do repo) a variável com a chave, ex.: `QSOFT_LLM_API_KEY`. |
| Cadastro | Provider `kind=OPENAI_COMPATIBLE`, `baseUrl` do fornecedor, `credentialRef=QSOFT_LLM_API_KEY`, `timeoutMs`. Modelo com `externalName` do fornecedor e preços. Política do departamento. |
| `.env.example` | Acrescentar `QSOFT_LLM_API_KEY=` **vazio**, com comentário. Nunca um valor. |
| Documentação | Atualizar `docs/development/HOW_TO_RUN.md` com uma seção "Provider real", sem segredos. |

Fornecedores compatíveis com `POST {baseUrl}/chat/completions`: OpenAI, Groq, Azure OpenAI
(endpoint compatível) e Ollama local (`http://localhost:11434/v1`, sem chave). Se for Ollama, o
`credentialRef` pode ser nulo: o adaptador **não** envia `Authorization` quando
`credential_ref IS NULL`. Confirmar esse comportamento no código; se não existir, implementar.

## 4. Catálogo: backend (S4-P2)

| Rota | Permissão | Regra |
|---|---|---|
| `GET /api/v1/ai-gateway/departments/{id}/model-policy` (novo) | AI_TOOL:MANAGE, TO | `{ departmentId, defaultModelId, allowedModelIds[] }`; sem política → 200 com lista vazia e `default` nulo; depto de outro tenant → 404 (validar pelo `OrganizationQuery`) |
| `PUT /api/v1/ai-gateway/models/{id}` (novo) | AI_TOOL:MANAGE, TO | edita `name`, `externalName`, `inputPricePer1k` e `outputPricePer1k`; preço negativo → 400; nome duplicado no tenant → 409; não altera status nem provider; mudança de preço **não** altera custos passados (o preço já está fotografado na tentativa, ADR-38) |

## 5. Catálogo: tela (SPI-87)

| Tela | Requisito |
|---|---|
| Arquivo | `public/js/pages/onboarding/step-ia.js` e a view `modules/ia/views/ia.ejs` |
| Rotas | Trocar `/ia/*` por `/api/v1/ai-gateway/providers`, `/models`, `/models/{id}/status`, `/models/{id}` (PUT) e `/departments/{id}/model-policy` (GET/PUT) |
| Listagem | Modelos com provider, status, preços e departamentos autorizados. Montar o mapa depto → modelos com `GET /departamentos` + `GET model-policy` por depto |
| Edição | Na própria tela: status, preços, nome externo e, por departamento, modelos permitidos e padrão (ADR-08: o padrão ∈ permitidos) |
| Segredo | Nunca exibir nem pedir o segredo. O campo `credentialRef` é só o **nome** da variável |
| Permissão | A tela só aparece para quem tem `AI_TOOL:MANAGE` (`/me/permissoes`). O backend decide |
| Persistência | Sem fallback para `localStorage`. Erro da API → mensagem, nunca sucesso simulado |

## 6. Testes

| ID | Cenário | Tipo |
|---|---|---|
| PR01 | Modelo sem preço não vira AVAILABLE (400); com preço 0 explícito vira | unit + IT |
| PR02 | Completion com modelo AVAILABLE legado sem preço → INCONCLUSIVE, sem `UsageRecord`, chat 200 com `statusCusto=INCONCLUSIVE` | IT |
| PR03 | OpenAI-compatível contra um servidor HTTP local de teste (`com.sun.net.httpserver.HttpServer` do JDK, **sem dependência nova**): request correto (model, messages, Authorization), `usage` mapeado, 429/500 → `AIProviderFailureException` (502), timeout → 504 | IT |
| PR04 | `credentialRef` nulo → sem header `Authorization`; variável ausente → `CREDENTIAL_MISSING`, 502, sem segredo no log | unit |
| PR05 | Ponta a ponta: chat → provider HTTP de teste → recibo → `UsageRecord`, com os valores batendo (SPI-138/133) | IT |
| PR06 | `GET model-policy`: outro tenant → 404; sem política → vazio | IT |
| PR07 | `PUT models/{id}`: preço novo não altera tentativa antiga (TR09) | IT |
| PR08 | Front `step-ia.js`: lista, edita status e preço, salva a política (teste `node --test` com fetch simulado) | unit front |
