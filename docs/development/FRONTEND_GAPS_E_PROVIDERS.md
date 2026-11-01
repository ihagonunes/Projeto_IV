# Correções de frontend e testes com providers externos

Atualizado em: 30/10/2026.

Branch: `IhanDevelopment`.

Identificação local: `FEAT-DASH-001` (não é uma issue Jira remota).

Este documento registra as correções de frontend aplicadas na branch e o
procedimento para testar o chat com chaves reais de providers compatíveis com a
API OpenAI. Nenhum contrato Java, migration ou regra de persistência foi
alterado por essas correções de frontend.

## 1. Alterações implementadas

### 1.1 Filtro operacional de departamento

Arquivo: `src/main/resources/static/public/js/pages/dashboard-metrics.js`.

- O dashboard operacional carrega `GET /api/v1/departamentos`.
- Os departamentos retornados são adicionados ao select `dashboard-area`.
- O select só é habilitado após a resposta bem-sucedida.
- Sem `DEPARTMENT:READ`, o select permanece desabilitado e o dashboard continua
  funcionando com "Todos os departamentos".
- O dashboard pessoal (`/metrics/me/*`) não consulta departamentos.
- O valor selecionado é enviado como `departmentId` para:
  - `GET /api/v1/observability/metrics/usage`;
  - `GET /api/v1/observability/metrics/costs`.
- Opções históricas continuam usando o prefixo `historical:` e são tratadas
  separadamente pelo script histórico.

### 1.2 Proteção dos shells administrativos

Arquivos:

- `src/main/resources/static/modules/auth/browser-session.js`;
- `src/main/resources/static/app.js`.

O middleware de sessão mantém as permissões de
`GET /api/v1/usuarios/me/permissoes` em `res.locals.permissions` e expõe
`requirePermission(permission)`.

Guardas aplicadas no BFF:

| Rota | Proteção |
|---|---|
| `/convites` | sessão + `INVITE:MANAGE` |
| `/admin/convites` | sessão + `INVITE:MANAGE` |
| `/admin/usuarios` | sessão + `USER:MANAGE` |
| `/onboarding/admin` | sessão + `TENANT:MANAGE` |
| `/onboarding/departamentos` | sessão + `TENANT:MANAGE` |
| `/onboarding/usuarios` | sessão + `TENANT:MANAGE` |
| `/onboarding/ia` | sessão + `AI_TOOL:MANAGE` |
| `/onboarding/revisao` | sessão + `TENANT:MANAGE` |

`/onboarding` continua exigindo apenas sessão, pois é a navegação comum dos
perfis autenticados. A autorização definitiva dos dados continua no Spring.

`/dashboard/:profile` já validava o layout com `Object.hasOwn` e redirecionava
administrador para `/onboarding`; o comportamento foi preservado.

### 1.3 JWT_SECRET no Compose

O `compose.yaml` exige:

```yaml
JWT_SECRET: "${JWT_SECRET:?JWT_SECRET must be set}"
```

Sem `JWT_SECRET`, o `docker compose` falha antes de subir o backend. A chave não
é gravada no banco nem no repositório.

O volume do PostgreSQL 18 também foi ajustado para `/var/lib/postgresql`, que é
o caminho exigido pela imagem `postgres:18-alpine`. O caminho anterior
(`/var/lib/postgresql/data`) fazia o container encerrar na inicialização.

### 1.4 Testes

Arquivos:

- `src/test/frontend/dashboard-metrics.test.js`;
- `src/test/frontend/browser-session.test.js`;
- `src/test/frontend/environment.test.js`.

Cobertura nova:

- carregamento dos departamentos autorizados e envio do `departmentId`;
- ausência da consulta de departamentos no dashboard pessoal;
- shell administrativo permitido com a permissão correta;
- shell administrativo negado sem sessão ou sem permissão;
- exigência de `JWT_SECRET` no Compose.

### 1.5 Evidências de execução

| Verificação | Resultado |
|---|---|
| `node --test src/test/frontend/*.test.js` | 67 testes, 0 falhas |
| `mvn -B test` | 847 testes, 0 falhas |
| `scripts/check --changed` | sucesso |
| `scripts/check` | sucesso |
| `docker compose config --quiet` com variáveis dummy | sucesso |

### 1.6 Smoke test real do OpenCode Go

Em 30/10/2026, com o backend em `8081` e o BFF em `3000`:

- chamada direta ao OpenCode Go: HTTP 200, conteúdo `ok`, 35 tokens de entrada e
  30 de saída;
- chat pelo BFF: HTTP 201, modelo `DeepSeek V4.1 Flash (Go)`, provider
  `OpenCode Go`, 37 tokens de entrada e 44 de saída, latência de 2.205 ms;
- dashboard como gestor: HTTP 200, filtro de departamento visível, um
  departamento (`Engenharia`) e uso operacional contabilizado.

O cliente agora envia `User-Agent: atlas-qsoft-governance/1.0` e
`x-opencode-session` para URLs que contenham `opencode.ai`. A sessão é gerada
por requisição. Para cache por conversa, o próximo passo seria propagar o ID da
conversa até o provider.

### 1.7 Dashboard do colaborador e seletor de modelos

O dashboard do colaborador passou a exibir também o uso operacional pessoal:

- a seção `Meu uso operacional` usa `/metrics/me/usage` e `/metrics/me/costs`;
- o cartão de usuários ativos fica oculto no perfil pessoal;
- o histórico importado continua separado;
- o filtro de terceiros e o orçamento não são exibidos.

O seletor de modelos do chat passou a usar `GET /api/v1/conversas/modelos`:

- mostra apenas modelos disponíveis na política do departamento;
- coloca o modelo padrão primeiro;
- envia o `requestedModelId` selecionado no próximo turno.

Em teste real com um colaborador:

- uma conversa gerou `requests=1` em `/metrics/me/usage`;
- `/metrics/me/costs` retornou uma linha;
- a página `/dashboard` exibiu `Meu uso operacional`.

### 1.8 Cadastro de API pelo administrador

O cadastro pela **API REST** está funcional e foi validado:

- `POST /api/v1/ai-gateway/providers`;
- `POST /api/v1/ai-gateway/models`;
- `PUT /api/v1/ai-gateway/departments/{id}/model-policy`;
- chat do colaborador usando o modelo cadastrado.

A tela `/onboarding/ia` foi corrigida na P2 (SPI-87) e passou a usar os endpoints
reais do `aigateway`:

- lista providers (sem expor segredo; `credentialRef` é só o nome da variável),
  modelos com provider/status/preços e a política por departamento;
- edita nome, `externalName`, preços e status do modelo;
- salva a política do departamento com padrão ∈ permitidos;
- não usa dados fictícios, `localStorage` nem sucesso simulado.

O cadastro de provider/modelo novo continua sendo feito pela API REST
administrativa; a tela edita o catálogo existente.

## 2. Limites atuais do provider

O projeto implementa apenas `OPENAI_COMPATIBLE` com:

```text
POST {baseUrl}/chat/completions
Authorization: Bearer {variável apontada por credentialRef}
```

Consequências:

- não há suporte a `/responses`;
- não há suporte ao protocolo Anthropic `/messages`;
- não há streaming/SSE;
- não há headers customizados por provider;
- não há OAuth;
- `credentialRef` é o nome de uma variável de ambiente do backend;
- a chave não é armazenada no banco;
- o modelo precisa usar o ID externo exato do provider;
- o custo é calculado com os preços cadastrados localmente;
- se o provider não devolver `usage`, o custo fica inconclusivo;
- HTTP 429 do provider vira falha de provider e a API responde 502;
- não há retry automático de chamadas ao provider.

## 3. Matriz de providers

| Provider | Base URL para `baseUrl` | Variável | Free tier | Compatível |
|---|---|---|---|---|
| OpenCode Go | `https://opencode.ai/inference/go/openai/v1` | `OPENCODE_API_KEY` | Planos Go são pagos; há modelos free promocionais | Sim, com modelos que usam `/chat/completions` |
| OpenCode Console | `https://opencode.ai/inference/openai/v1` | `OPENCODE_API_KEY` | Modelos free exigem o cliente OpenCode | Sim, para modelos pagos com `/chat/completions` |
| Groq | `https://api.groq.com/openai/v1` | `GROQ_API_KEY` | Sim, Free plan | Sim, para chat |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `GEMINI_API_KEY` | Sim, para Flash/Flash-Lite | Sim, com ressalvas de privacidade |
| Cerebras | `https://api.cerebras.ai/v1` | `CEREBRAS_API_KEY` | Trial de US$ 5 por 30 dias; não é permanente | Sim, para teste curto |
| xAI Grok | `https://api.x.ai/v1` | `XAI_API_KEY` | Não; API paga | Sim tecnicamente |
| STUB local | não usa rede | não usa chave | sempre local | Sim; teste determinístico |

### 3.1 OpenCode Go

O endpoint oficial atual é:

```text
https://opencode.ai/inference/go/openai/v1
```

Para chat completions:

```text
https://opencode.ai/inference/go/openai/v1/chat/completions
```

O documento oficial também lista endpoints por modelo, como
`/inference/go/openai/v1/responses` e
`/inference/go/anthropic/v1/messages`. O projeto não suporta esses dois
protocolos.

Modelos nativos de Chat Completions no Go incluem:

- `glm-5.3-flash`;
- `glm-5.3`;
- `deepseek-v4.1-flash`;
- `deepseek-v4-pro`;
- `kimi-k3`;
- `kimi-k2.7-code`;
- `longcat-2.0`;
- `mimo-v2.6-flash`.

Para o primeiro teste, prefira um modelo nativo de `/chat/completions`, como
`glm-5.3-flash` ou `deepseek-v4.1-flash`.

A consulta a `https://opencode.ai/inference/go/v1/models` retornou 28 modelos
em 30/10/2026. Foram testados via `/chat/completions` com sucesso:
`glm-5.3-flash`, `grok-4.7`, `claude-haiku-5-5`, `gemini-4-argon`,
`qwen3.8-max` e `minimax-m3`. Isso indica que o Console traduz também modelos
nativos de `/responses` e `/messages` para Chat Completions.

Ainda assim, não há importação automática: cada modelo precisa ser cadastrado
individualmente com `externalName` e preços. O seletor do chat lista todos os
modelos disponíveis que estiverem na política do departamento.

Pontos de atenção:

- o Go é uma assinatura paga; os modelos free promocionais mudam com o tempo;
- o cliente envia `User-Agent: atlas-qsoft-governance/1.0` e
  `x-opencode-session` quando a URL contém `opencode.ai`;
- a sessão é gerada por requisição; para cache ideal por conversa, o próximo
  passo é propagar o ID da conversa até o provider;
- a documentação atual usa `OPENCODE_API_KEY` como variável da chave;
- guias antigos podem citar `https://opencode.ai/zen/go/v1`; confirme o endpoint
  vigente no Console.

### 3.2 OpenCode Console

- Base: `https://opencode.ai/inference/openai/v1`.
- Modelos free do OpenCode só funcionam dentro do cliente OpenCode; outros
  clientes recebem 403, mesmo com chave.
- Use modelos pagos do Console ou o endpoint Go.
- A chave também é `OPENCODE_API_KEY`.

### 3.3 Groq

- Base: `https://api.groq.com/openai/v1`.
- Free plan disponível.
- Exemplos de modelo: `openai/gpt-oss-120b`, `openai/gpt-oss-20b`,
  `qwen/qwen3.8-27b`.
- Groq declara que não treina com dados de API por padrão e oferece Zero Data
  Retention; confirme os termos vigentes.
- É a opção gratuita contínua mais simples para um smoke test.
- O 429 vira 502 no projeto; não faça carga em massa no free tier.

### 3.4 Gemini

- Base: `https://generativelanguage.googleapis.com/v1beta/openai`.
- Free tier para Flash/Flash-Lite; Pro não está no free tier.
- Na camada gratuita, o Google pode usar entradas/saídas para melhorar produtos
  e pode haver revisão humana. Na camada paga, isso não ocorre.
- Para dados corporativos ou confidenciais, use a camada paga ou não use.
- Confirme o modelo disponível no AI Studio, por exemplo `gemini-3.8-flash`.
- Confirme suporte a tool calls antes de testar o agente de políticas.

### 3.5 Cerebras

- Base: `https://api.cerebras.ai/v1`.
- A documentação oficial descreve Free Trial, não free tier permanente:
  aproximadamente US$ 5 em créditos, expiração em 30 dias e método de pagamento
  verificado.
- Depois do trial, é pago.
- Serve para um teste curto.

### 3.6 xAI Grok

- Base: `https://api.x.ai/v1`.
- API paga por token; não há free tier de API.
- O Playground gratuito não é a API.
- Não confundir com Groq: Groq é o provedor de inferência com free tier; xAI
  Grok é o modelo/API paga da xAI.

## 4. Como testar uma chave real

### 4.1 Antes de configurar no projeto

Confirme a chave diretamente no provider, fora do projeto. Isso isola problemas
de chave, modelo e endpoint. Não cole a chave em arquivos do repositório.

Exemplo PowerShell para OpenCode Go:

```powershell
$headers = @{
    Authorization = "Bearer $env:OPENCODE_API_KEY"
    "x-opencode-session" = [guid]::NewGuid().ToString()
    "Content-Type" = "application/json"
}
$body = @{
    model = "glm-5.3-flash"
    messages = @(@{ role = "user"; content = "Responda apenas: ok" })
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
    -Uri "https://opencode.ai/inference/go/openai/v1/chat/completions" `
    -Method Post -Headers $headers -Body $body
```

Exemplo Groq:

```powershell
$headers = @{
    Authorization = "Bearer $env:GROQ_API_KEY"
    "Content-Type" = "application/json"
}
$body = @{
    model = "openai/gpt-oss-120b"
    messages = @(@{ role = "user"; content = "Responda apenas: ok" })
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
    -Uri "https://api.groq.com/openai/v1/chat/completions" `
    -Method Post -Headers $headers -Body $body
```

Exemplo Gemini:

```powershell
$headers = @{
    Authorization = "Bearer $env:GEMINI_API_KEY"
    "Content-Type" = "application/json"
}
$body = @{
    model = "gemini-3.8-flash"
    messages = @(@{ role = "user"; content = "Responda apenas: ok" })
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
    -Uri "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" `
    -Method Post -Headers $headers -Body $body
```

Se o provider responder com `choices[0].message.content`, a chave e o modelo
estão corretos. Se responder 401/403, a chave ou o modelo não está liberado.

### 4.2 Configurar no projeto

A chave deve estar no ambiente do **backend**, não no banco, no frontend, no
`compose.yaml` ou no Git.

Crie o provider como administrador:

```http
POST /api/v1/ai-gateway/providers
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "name": "OpenCode Go",
  "kind": "OPENAI_COMPATIBLE",
  "baseUrl": "https://opencode.ai/inference/go/openai/v1",
  "credentialRef": "OPENCODE_API_KEY",
  "timeoutMs": 60000
}
```

Crie o modelo:

```http
POST /api/v1/ai-gateway/models
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "providerId": "<uuid-do-provider>",
  "name": "GLM 5.3 Flash (Go)",
  "externalName": "glm-5.3-flash",
  "inputPricePer1k": 0.00015,
  "outputPricePer1k": 0.0005
}
```

`inputPricePer1k` e `outputPricePer1k` são por 1.000 tokens. O valor acima é uma
conversão do preço oficial por 1 milhão de tokens do GLM-5.3-Flash no Go
(US$ 0,15 de entrada e US$ 0,50 de saída por 1M). Preços mudam; confira o Console.

Para Groq free, use preço explícito `0.0` nos dois campos, apenas para
demonstração. O projeto aceita zero explícito, mas não aceita preço ausente.

Configure a política do departamento:

```http
PUT /api/v1/ai-gateway/departments/<uuid-do-departamento>/model-policy
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "allowedModelIds": ["<uuid-do-modelo>"],
  "defaultModelId": "<uuid-do-modelo>"
}
```

### 4.3 Testar pelo chat

O usuário precisa ter `CHAT:CREATE`/`CHAT:READ` e pertencer ao departamento.
`TENANT_ADMIN` sozinho não tem chat.

```http
POST /api/v1/conversas
Authorization: Bearer <token-do-usuario>
Content-Type: application/json

{
  "mensagem": "Explique em uma frase o que é governança de IA.",
  "requestedModelId": null
}
```

Confira na resposta:

- `conversa.id`;
- mensagem `ASSISTANT`;
- `recibo.modelo`;
- `recibo.provider`;
- `recibo.tokensEntrada` e `recibo.tokensSaida`;
- `recibo.custoEstimado` e `recibo.statusCusto`;
- `recibo.latenciaMs`.

Depois:

```http
GET /api/v1/conversas?page=0&size=20
Authorization: Bearer <token-do-usuario>
```

## 5. O que observar no dashboard

1. Entre como gestor ou executivo em `/dashboard`.
2. O select de departamento deve listar os departamentos autorizados.
3. Escolha um departamento e aplique o filtro.
4. Faça uma conversa pelo `/chat` com um usuário do mesmo departamento.
5. Atualize o dashboard.
6. Verifique:
   - requisições;
   - usuários ativos;
   - tokens de entrada/saída;
   - custo estimado;
   - tabela de custo por modelo.

As chamadas do chat alimentam:

- `aigateway_request_attempts`;
- `observability_usage_records`, via `AIRequestCompleted`.

Chamadas feitas diretamente ao provider por script ou Postman **não** aparecem
no dashboard. Para inspecionar tentativas, use
`GET /api/v1/ai-gateway/tentativas` com `USAGE:READ`.

Se o dashboard não mudar:

- confirme que a conversa foi concluída com `ASSISTANT`;
- confirme que o usuário é do departamento filtrado;
- confirme que o período do filtro inclui a data da conversa;
- confirme o status da tentativa em `/api/v1/ai-gateway/tentativas`;
- verifique se o provider retornou `usage`; sem `usage`, o custo fica
  inconclusivo.

## 6. Limitações e riscos

- **OpenCode Go com sessão por requisição:** o header é enviado, mas ainda não é
  estável por conversa; isso pode reduzir o aproveitamento de cache.
- **Free tiers:** limites baixos; 429 vira 502 e não há retry automático.
- **Privacidade:** Gemini free pode usar dados para melhorar produtos; avalie
  antes de enviar conteúdo corporativo.
- **Chave por tenant:** `credentialRef` aponta para uma variável global do
  processo; não há cofre de segredo por tenant.
- **Preço local:** o custo exibido é estimativa, não faturamento.
- **Tool calls:** chat simples funciona; o agente exige suporte do modelo.
- **Tela de IA do onboarding:** é legada; o cadastro administrativo funcional é
  somente pela API REST.
- **Catálogo Go:** não há sincronização automática dos modelos do provider; cada
  modelo precisa ser cadastrado manualmente com ID e preço.

## 7. Recomendação de teste

1. STUB para validar o fluxo interno.
2. Groq Free para o primeiro teste real de chat.
3. OpenCode Go com `glm-5.3-flash` ou `deepseek-v4.1-flash`.
4. Gemini Free apenas se os termos de dados forem aceitáveis.
5. Cerebras apenas como teste curto.
6. xAI Grok apenas se houver orçamento pago.

O chat com OpenCode Go foi validado pelo BFF. O próximo passo opcional é
propagar o ID da conversa como `x-opencode-session` para melhorar roteamento e
cache por conversa.
