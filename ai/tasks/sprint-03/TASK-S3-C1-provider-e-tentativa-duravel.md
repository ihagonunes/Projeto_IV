# TASK-S3-C1 — `AICompletionService`, adaptadores de provider e tentativa durável

## Metadados
- **Onda:** C (execução de IA)
- **Depende de:**
  - ADR-38 ACCEPTED;
  - **decisão 11** só para configurar o provider real da demo — o código e os testes usam o `STUB`.
- **Jira:**
  - SPI-127 (redefinir: "cliente de provider sem SDK", em vez de Spring AI);
  - **SPI-171** (tentativas duráveis de IA, N05/RF047);
  - SPI-74 continua fechada.
- **Módulo proprietário:** `aigateway`
- **Spec:** `SPEC-03-execucao-ia.md` §1, §2, §3 (passos 1–4), §5 (TR01, TR02, TR03, TR08, TR09, TR11, TR12)

## Critérios de aceitação
- [ ] `V017__aigateway_provider_config_and_attempts.sql` exatamente como na SPEC-03 §1.
- [ ] Contratos públicos da SPEC-03 §2.1 em `aigateway.api`, incluindo as exceções públicas.
- [ ] Porta `ProviderClient` (§2.2) com três implementações:
  - [ ] `StubProviderClient`, com todos os modos da tabela §2.3;
  - [ ] `OpenAiCompatibleProviderClient` (`RestClient` do Spring, **sem dependência nova**);
  - [ ] `EnvCredentialResolver`.
- [ ] `CompleteAIRequest` reescrito como implementação de `AICompletionService`:
  - [ ] o `requestId` é sempre gerado no servidor;
  - [ ] o departamento é resolvido pelo `IdentityQuery`;
  - [ ] grava DENIED, IN_FLIGHT (`REQUIRES_NEW`, antes da chamada) e o resultado final.
- [ ] Nesta tarefa, o resultado `PENDING` é publicado **sem** reentrega. A reentrega é a C2.
- [ ] O CRUD de providers e models aceita `kind`, `baseUrl`, `credentialRef`, `timeoutMs` e
      `externalName`. Respostas **nunca** trazem o segredo.
- [ ] Ninguém loga prompt, resposta, header `Authorization` nem segredo. Há um teste que captura o
      log e prova isso.
- [ ] Testes TR01, TR02, TR03, TR08, TR09, TR11 e TR12 verdes.
- [ ] `ai/dependencies.md` registra: "provider via HTTP (`RestClient`), sem SDK".

## Fora de escopo
Job de reentrega, listener sem `catch` e `ON CONFLICT` (C2); endpoint RF047 (C3); chat (D1).

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
