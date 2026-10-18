# TASK-S3-E2 — Orquestração do agente, ferramentas e endpoint de perguntas

## Metadados
- **Onda:** E · **Depende de:** E1, C1 (o `AICompletionService` com *tools*; o stub `stub-tool`)
- **Jira:** SPI-132, SPI-130 (geração), SPI-135
- **Módulo proprietário:** `agentpolicies`
- **Spec:** `SPEC-05` §3, §4, §6 (ferramentas, prompt e schema de execução)

## Critérios de aceitação
- [ ] `AskPolicyAgent` segue o fluxo da SPEC-05 §3:
  - [ ] execução RUNNING → COMPLETED/FAILED;
  - [ ] no máximo 2 rodadas de ferramentas;
  - [ ] allowlist de ferramentas: `buscar_politicas`, `modelo_permitido`;
  - [ ] cada ferramenta roda com o contexto do usuário.
- [ ] Sem nenhum trecho recuperado, o LLM não é chamado e a resposta é "não encontrado".
- [ ] `POST /api/v1/agente-politicas/perguntas` (KNOWLEDGE_DOCUMENT:READ, BT), com o payload da
      SPEC-05 §4.
- [ ] O prompt de sistema é fixo, fica no código e é coberto por um teste unitário de montagem.
- [ ] Nada de texto de pergunta ou resposta fora da resposta HTTP. Há um teste que captura o log.
- [ ] Testes:
  - [ ] IT: a `ProviderCall` capturada pelo stub não contém o texto de documento de outro depto;
  - [ ] IT de ferramentas (`stub-tool`);
  - [ ] ferramenta desconhecida é ignorada e registrada;
  - [ ] erros da completion mapeados (502, 503, 504, 409, 403).
- [ ] `ai/contracts.md` documenta o endpoint e o `KnowledgeAccessService`.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
