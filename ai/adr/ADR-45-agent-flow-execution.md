# ADR-45 — Execução determinística de pipelines de agentes

## Status

ACCEPTED (2026-11-22) — decisão do responsável pela plataforma (SPI-180), após
a entrega do BE-05. O MVP executa de forma **síncrona**, com fail-fast,
`merge=CONCATENATE`, timeout por etapa/total, paralelismo máximo 2 e sem retry
automático (a classificação de falha transitória exige uma porta nova no
`aigateway`). A integração com o chat continua fora deste ADR e exige emenda +
allowlist.

## Contexto

- O ADR-44 cria o módulo `agents` e persiste catálogo, fluxos e versões.
- A UI atual só monta um rascunho local; não executa nem altera o chat.
- `chat` executa **uma** completion por turno e devolve **um** recibo
  (`ChatTurnResponse`/`MessageResponse`). Um pipeline com ramos gera várias
  completions.
- `aigateway` já expõe `AICompletionService` (ADR-38) e registra tentativas,
  custo, latência e entrega de uso. `RequestPurpose` tem `CHAT` e `POLICY_AGENT`.
- `AgentExecution` (agentpolicies) tem um `requestId` e estados
  `RUNNING/COMPLETED/FAILED`; não modela etapas, ramos, merge ou falha parcial.
- `UsageRecord` não tem `agent_id`; a lacuna de custo por agente já está
  registrada nos documentos de dashboard.
- Não há broker/fila distribuída; o deploy é um monólito modular com um
  PostgreSQL.

## Decisão proposta

### 1. Execução separada do chat no MVP

O fluxo é executado por:

```text
POST /api/v1/agentes/fluxos/{id}/execucoes
GET  /api/v1/agentes/execucoes/{id}
```

O `POST` recebe a entrada do usuário e uma `Idempotency-Key` (ou
`clientExecutionId`), valida a versão publicada e retorna o status/resultado. O
chat só será integrado depois, com `agentFlowId` opcional e múltiplos recibos;
essa alteração exige emenda a este ADR e à allowlist `chat -> agents.api`.

### 2. Modelo de execução

Status da execução: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`,
`CANCELLED`.

Cada nó tem status próprio e metadados de execução. O resultado final é o
resultado do nó `result`. Não há execução parcial silenciosa.

### 3. Semântica do DAG no MVP

- Exatamente uma `task` e um `result` por versão publicada.
- `task` só tem saída; `result` só tem entrada.
- Sem ciclos, autoloops, arestas duplicadas ou nós órfãos.
- `result` precisa ser alcançável a partir de `task`.
- `merge` exige ≥2 entradas e produz uma saída.
- Fan-out é permitido, com limite de paralelismo e de nós.
- O MVP executa em níveis topológicos; ramos prontos podem rodar em paralelo.
- **Merge do MVP:** `CONCATENATE` determinístico, na ordem estável das arestas de
  entrada. `SUMMARIZE` (outra completion) fica para uma decisão posterior, porque
  adiciona custo, latência e não determinismo.
- **Falha do MVP:** fail-fast. A primeira etapa que falhar encerra a execução como
  `FAILED`; não há resultado final inventado.
- **Timeout:** por etapa e total, com cancelamento propagado. Etapa que excede o
  timeout vira `TIMED_OUT`.
- **Retry:** apenas para falhas transitórias e seguras. `OUTCOME_UNKNOWN` não é
  repetido automaticamente, para não duplicar custo.
- **Limites iniciais propostos:** máximo de 20 nós, 40 arestas, 64 KB de grafo,
  2 ramos paralelos e 120 s de execução total. Valores finais devem ser
  configuráveis e testados.

### 4. Cada agente é uma completion

Cada nó `AGENT` executa uma chamada a `AICompletionService` com:

- `tenantId`/`userId` do contexto de execução;
- modelo resolvido pela política do departamento (ADR-08); não aceitar modelo
  arbitrário do cliente;
- prompt/template da versão do agente;
- allowlist de ferramentas do agente;
- escopo de conhecimento do agente;
- `purpose = AGENT_FLOW` (novo valor em `RequestPurpose`, com contrato/teste);
- mensagens de entrada derivadas do nó anterior e da tarefa.

O `aigateway` continua sendo o único caminho para o provider e a única origem de
`AIRequestCompleted`/`UsageRecord`. Nenhuma tabela de outro módulo é lida.

### 5. Recibos e custo

- Cada step guarda `request_id`; os recibos são buscados em lote via
  `AttemptReceiptQuery` (ADR-38/42) ou porta equivalente.
- O custo da execução é a soma dos recibos das etapas; não copiar tokens/custo
  para `agents_flow_steps` como fonte primária.
- `INCONCLUSIVE` não vira zero; a execução deve expor custo parcial/desconhecido.
- O resultado final e os metadados de execução não incluem prompt/resposta em
  logs, eventos ou analytics. A retenção do conteúdo final é decisão de produto
  (LGPD).
- Não adicionar `agent_id` a `UsageRecord` sem necessidade; o vínculo por
  `request_id` + `agents_flow_steps` evita duplicar a fonte e mantém a fronteira.

### 6. Autorização e isolamento

- `AGENT:RUN` com `BY_TARGET`; checagem fina de dono/departamento/tenant.
- `AGENT:MANAGE` para criar/editar/publicar; `AGENT:READ` para catálogo/fluxos.
- Outro tenant → 404; sem permissão/fora do escopo → 403.
- O LLM nunca decide autorização; ferramentas e conhecimento são validados antes
  da completion.
- O admin continua sem acesso a conversas; se o chat for integrado, o teto SELF
  do chat prevalece.
- Idempotência por `Idempotency-Key`; repetição devolve a mesma execução e não
  duplica tentativas/custo.
- Limite de execuções concorrentes por tenant/usuário para evitar abuso.

### 7. Síncrono primeiro; assíncrono depois

O MVP pode ser síncrono com limites duros (poucos nós, timeout total curto). Se
o produto exigir pipelines maiores:

- persistir a execução como `PENDING` e processar com um executor interno
  limitado;
- expor polling em `GET /execucoes/{id}`;
- recuperar execuções `RUNNING` órfãs após restart;
- não introduzir broker/Redis sem necessidade medida e ADR.

### 8. Integração futura com o chat

Quando aprovada:

- `SendMessageRequest`/`SendMessageCommand` ganham `agentFlowId` opcional;
- `chat` passa a depender de `agents.api` (allowlist + ArchUnit);
- `ChatTurnResponse`/`MessageResponse` passam a representar múltiplos recibos ou
  um recibo de execução com steps;
- a mensagem ASSISTANT final continua privada ao dono; o histórico não expõe
  etapas de outros usuários;
- o envio sem `agentFlowId` mantém o comportamento atual.

## Alternativas consideradas

- **Executar dentro do chat já no MVP:** acopla cedo, exige mudança de contrato de
  recibo e amplia o risco de privacidade.
- **Chamar provider diretamente no módulo agents:** viola ADR-11/38 e duplica
  tentativa/uso.
- **Merge com LLM desde o início:** mais caro, não determinístico e sem contrato
  de avaliação.
- **Assíncrono com broker:** infraestrutura nova sem necessidade comprovada.
- **Executar os 18 agentes de uma vez:** aumenta custo, ambiguidade de prompt e
  superfície de falha; o MVP deve começar com 3 agentes.

## Consequências

- Novas tabelas de execução/etapas (ADR-44).
- Novo valor de `purpose` e novos testes de contrato em `aigateway`.
- Endpoint de execução/polling e limites configuráveis.
- Testes de concorrência, timeout, falha parcial, idempotência e isolamento.
- Integração com chat e dashboards fica para uma fase posterior.
- Com o ADR `ACCEPTED` (2026-11-22), a praça executa versões publicadas pelo
  endpoint próprio, sem alterar o turno do chat.
