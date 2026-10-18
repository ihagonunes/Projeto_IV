# ADR-38 — Provider real, registro durável de tentativas e entrega idempotente de uso

## Status

ACCEPTED (2026-10-18). O provider concreto da demo (decisão 11) ainda será informado pelo responsável; até lá, o código usa o adaptador `STUB`.

## Contexto

- `CompleteAIRequest` não tem caller de produção e não chama provider algum: recebe tokens prontos
  no comando e aceita `requestId` do chamador.
- O listener do `observability` engole exceções e perde o `UsageRecord` (N05, hoje latente).
- Falhas e custo desconhecido não cabem em `UsageRecord`, que exige `estimated_cost NOT NULL`
  (A21/RF047).
- `aigateway_providers` não guarda tipo, endereço nem referência de credencial.
- Chat e agente de políticas (Sprint 3) precisam de uma completion real.

## Decisão

1. **Porta de completion.** `aigateway.api` expõe `AICompletionService` (síncrono, ADR-11). É o
   único caminho para um LLM. Não existe `/ai/complete` público.
2. **Porta de provider.** `aigateway.application.port.ProviderClient` recebe a requisição já
   autorizada e devolve conteúdo e uso. O uso é `Known(inputTokens, outputTokens)` ou `Unknown`.
   Há dois adaptadores em `aigateway.infrastructure.provider`:
   - `OPENAI_COMPATIBLE`: HTTP via `RestClient` do Spring, **sem SDK e sem dependência nova**. Atende
     OpenAI, Groq, Azure OpenAI e Ollama, que expõem a API `/v1/chat/completions`.
   - `STUB`: determinístico, para testes e demo offline.
3. **Credencial (ADR-10).** O provider guarda só `credential_ref`, o nome de uma variável de
   ambiente. A infraestrutura resolve o segredo; ele nunca aparece em log, evento ou DTO.
4. **Tentativa durável.** A tabela `aigateway_request_attempts` pertence ao `aigateway`. A intenção
   (`GRANTED` + `IN_FLIGHT`) é gravada e confirmada em transação própria **antes** da chamada ao
   provider. O estado tem três dimensões (autorização, execução, entrega) e `cost_status`
   (`KNOWN`, `NOT_APPLICABLE`, `INCONCLUSIVE`). A tabela não tem coluna de conteúdo.
5. **Entrega idempotente.** Depois do commit do resultado, o `aigateway` publica
   `AIRequestCompleted` (sem evento novo).
   - O listener do `observability` **não captura** exceção.
   - Quem captura é o publicador: em caso de falha, a tentativa fica `PENDING` com
     `next_delivery_at` em espera crescente.
   - Um job `@Scheduled` reentrega as pendentes vencidas com `FOR UPDATE SKIP LOCKED`; após N
     tentativas, marca `DEAD`.
   - O mesmo job converte `IN_FLIGHT` antigo em `OUTCOME_UNKNOWN`/`INCONCLUSIVE`.
   - `RecordUsage` grava com `ON CONFLICT (request_id) DO NOTHING`.
6. **`request_id` gerado só pelo servidor.**
7. **Custo.** Erro explícito do provider fica com custo `INCONCLUSIVE`, a menos que o adaptador
   declare "sem cobrança". `UsageRecord` registra só consumo conhecido (emenda ao ADR-13).

## Alternativas consideradas

- Spring AI: dependência nova e acoplamento a um framework; a API HTTP compatível com OpenAI basta.
- Broker ou outbox genérico: proibido sem gatilho medido.
- Evento `AIRequestFailed`: contrato novo sem consumidor.

## Consequências

- Uma migration (V017: colunas do provider, `external_name` do modelo e a tabela de tentativas).
- Um job agendado.
- Retenção da tabela de tentativas ainda a definir (decisão 12).
- O segredo do provider da demo precisa existir no ambiente (ex.: `QSOFT_LLM_API_KEY`), nunca no
  repositório.
