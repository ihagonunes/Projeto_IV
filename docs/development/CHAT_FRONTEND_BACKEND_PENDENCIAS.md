# Chat ATLAS — interface e pendências de backend

Data: 23/10/2026. Interface: SPI-136. Backend relacionado: SPI-172; recibos: SPI-134.

Este documento reúne os contratos consumidos pela interface e as pendências para integrar
os recursos adicionais. Nenhum código Java, migration, configuração de autenticação ou
contrato de API foi alterado. As rotas Express adicionadas apenas renderizam a interface.
As configurações locais do simulador utilizadas na validação não são distribuídas pelo Git.
Este documento não modifica ADRs nem aceita novas decisões.

## 1. Onde acessar e como experimentar

- `/chat`: interface conectada, protegida pelo middleware de sessão já existente.
- `/chat/previa`: interface pública com fixtures fictícias, isoladas em memória por página.
  Não consulta APIs, não lê arquivos e não usa localStorage/sessionStorage para conversas.
  Recarregar ou usar “Restaurar prévia” descarta as alterações fictícias.
- Entrada “Chat de IA” no onboarding para perfis não administrativos. Permissões reais
  continuam sendo verificadas pelo backend em cada operação.
- Iniciar o frontend com `npm start`. Não é necessário iniciar Spring/PostgreSQL para a prévia.
- Testes específicos: `node --test src/test/frontend/chat.test.js`.

## 2. Interface entregue

- Identidade ATLAS: variáveis de cor/fonte existentes, logos locais, fundo azul escuro e dourado.
- Navegação semelhante a um chat conversacional: sidebar recolhível, novo chat, busca,
  recentes/arquivadas, opções por conversa e histórico ordenado por atualização.
- Tela vazia com sugestões que preenchem o compositor, sem enviar automaticamente.
- Compositor expansível com limite de 8.000 caracteres; Enter envia no desktop,
  Shift+Enter quebra linha, Enter no celular quebra linha. IME não dispara envio.
- Respostas, cópia, blocos de código como texto, detalhes de execução e regeneração visual.
- Model picker, consumo, cota fictícia com aviso em 80% e cenário de cota atingida.
- Renomear, arquivar, restaurar, excluir com confirmação e regenerar funcionam na prévia.
- Anexo fictício removível: apenas nome de exemplo, sem leitura/upload de arquivo real.
- Erros por status, espera, interrupção da espera e reconciliação de mensagens após falha.
- Modais com dialog nativo, Escape, foco, rótulos acessíveis, indicador de espera e avisos.
- Layout móvel com sidebar sobreposta e compositor fixado pela estrutura flexível da página.
- Nenhum conteúdo da IA é inserido via innerHTML; HTML e URLs são tratados como texto.

### Limites deliberados da integração atual

O modo conectado usa os endpoints de ciclo de vida, busca, modelos, recibos e consumo
pessoal implementados na Sprint 4. Renomear, arquivar, restaurar e excluir só confirmam
sucesso após a resposta da API; em falha, a lista é reconciliada com o servidor.
Os recibos dos envios são recuperados de `GET /conversas/{id}/mensagens` ao reabrir a
conversa, sem duplicar tokens/custo em `chat_messages`. A busca e o status são
resolvidos no servidor (`GET /conversas?status=&q=`), não apenas sobre as conversas
carregadas. O consumo pessoal informativo usa
`GET /api/v1/observability/metrics/me/usage`.
Não há cota fictícia, moeda, saldo ou ocupação da janela inventados no modo conectado.
Regeneração de resposta e anexos continuam fora do contrato e mostram explicação.
O botão de parar aborta a espera HTTP; não afirma cancelar processamento/cobrança no servidor.

## 3. Contratos existentes preservados

| Operação | Contrato consumido |
|---|---|
| Criar e enviar | POST `/api/v1/conversas` |
| Listar histórico | GET `/api/v1/conversas?status=&q=&page=&size=` |
| Abrir mensagens | GET `/api/v1/conversas/{id}/mensagens?page=&size=` |
| Enviar na conversa | POST `/api/v1/conversas/{id}/mensagens` |
| Renomear/arquivar/restaurar | PATCH `/api/v1/conversas/{id}` |
| Excluir | DELETE `/api/v1/conversas/{id}` |
| Modelos permitidos | GET `/api/v1/conversas/modelos` |
| Consumo pessoal | GET `/api/v1/observability/metrics/me/usage` |

Envio: `{ "mensagem": "...", "requestedModelId": null }`.
Resposta: `{ "conversa": {...}, "mensagens": [...], "recibo": {...} }`.
Os dois registros de um turno são acrescentados ao histórico, não o substituem.
Dados seguem via cookie HttpOnly same-origin/BFF existente. POST inclui `X-Qsoft-Request: 1`.
O frontend não recebe nem armazena JWT e não envia tenant/owner no payload.

## 4. Backend necessário por recurso

Os caminhos e DTOs abaixo são propostas para discussão. Não são APIs implementadas.
Devem ser associados às issues apropriadas e respeitar ADR-16, ADR-36, ADR-38 e ADR-39.

> **Status (Sprint 4, 28/10/2026):** §4.1, §4.2, §4.5 e a idempotência de §4.6 foram
> implementados no backend (ADR-42). Os contratos finais estão em `ai/contracts.md` §Chat.
> Os itens abaixo ficam como registro da proposta original; o que não foi entregue está
> marcado em cada seção.

### 4.1 Modelos disponíveis para o usuário — prioridade alta

Necessidade: preencher o seletor com todos os modelos que o ator realmente pode usar,
identificar padrão, indisponibilidade e provider, sem usar catálogo administrativo.

Proposta: GET `/api/v1/conversas/modelos`, retornando:

```json
{
  "defaultModelId": "uuid",
  "models": [
    {
      "id": "uuid",
      "name": "Nome do modelo",
      "providerName": "Nome do provider",
      "description": "Descrição para o usuário",
      "available": true,
      "unavailableReason": null
    }
  ]
}
```

- Derivar tenant, ator e departamento no servidor; respeitar política e provider ativo.
- Não ampliar `AI_TOOL:MANAGE` para permitir o seletor de um colaborador.
- Envio deve revalidar permissão mesmo após carregar o seletor.
- Seleção atual vale para o próximo turno. Definir se uma preferência por conversa será
  persistida e como reagir quando ela deixar de ser permitida.
- Modelos da prévia são nomes fictícios, não um compromisso de catálogo comercial.

**Implementado (Sprint 4):** `GET /api/v1/conversas/modelos` usa
`ModelResolutionQuery.allowedModels` e devolve `defaultModelId` + `models[]` com
`id`, `name`, `providerName`, `available` e `unavailableReason`. `description` não foi
implementado. O envio continua revalidando a política.

### 4.2 Recibo durável e modelo por resposta — prioridade alta

Necessidade: exibir detalhes ao reabrir o chat, incluindo modelo efetivamente usado.
Preferir enriquecer a consulta de mensagens com `recibo` por ASSISTANT ou oferecer uma
consulta em lote por requestId limitada ao proprietário. Evitar varrer `/tentativas`.

Preservar os campos atuais e acrescentar moeda explícita quando houver definição:
`requestId`, modelo, provider, tokensEntrada, tokensSaida, custoEstimado, statusCusto,
latenciaMs e currency. Definir identidade estável do modelo para nomes alterados/removidos.
Usage desconhecido deve permanecer nulo. Custo sem preço não deve aparentar gratuidade;
revisar cálculo com preços nulos e parsing de usage parcial que atualmente usa zero.

**Implementado (Sprint 4):** `GET /api/v1/conversas/{id}/mensagens?page=&size=`
(padrão 50, máximo 200) devolve cada ASSISTANT com `recibo` vindo de
`AttemptReceiptQuery.receipts` (lote por tenant + dono, até 100 requestIds). Recibo
ausente → `recibo: null`. O custo sem preço agora é `INCONCLUSIVE` (não zero). `currency`
não foi implementado; `modelName`/`providerName` usam o catálogo atual.

### 4.3 Consumo, cota e alertas — prioridade alta

Necessidade: substituir totais de sessão e cota fictícia por valores oficiais do próprio ator.
Proposta: GET `/api/v1/conversas/consumo`:

```json
{
  "period": { "from": "ISO-8601", "to": "ISO-8601", "timezone": "UTC" },
  "inputTokens": 3200,
  "outputTokens": 5200,
  "usageStatus": "COMPLETE",
  "quota": { "limit": 10000, "used": 8400, "remaining": 1600, "alertThresholdPercent": 80 },
  "enforcement": "INFORMATIONAL",
  "updatedAt": "ISO-8601"
}
```

- Definir se existe cota pessoal, departamental ou ambas, seu período e reinício.
- Expor somente o recorte autorizado. O endpoint de orçamento departamental atual não
  atende necessariamente ao colaborador e não deve ser usado como saldo pessoal.
- Campos desconhecidos devem ser nulos e apresentar status de completude.
- Limiares 80% e 100% da prévia são exemplos de UI; a regra real deve vir do servidor.
- Decidir aviso versus bloqueio e status/código de erro. A UI atual não bloqueia por cota.
- Se houver bloqueio, implementá-lo no gateway com tratamento de concorrência; não no browser.
- Definir eventual custo agregado e moeda; não confundir AI Credits com tokens disponíveis.

### 4.4 Janela de contexto e estimativa pré-envio

A UI explica a regra atual de últimas 20 mensagens. Para um medidor percentual real,
o serviço precisa informar contextWindowTokens, outputReserveTokens, tokens estimados,
modelo/tokenizador usados e quais mensagens entram no recorte.
Se houver estimativa pré-envio, avaliar um contrato específico, privacidade, custo e latência.
Não calcular tokens por quantidade de caracteres como se fosse medição real.
Resumo automático e truncamento por tokens exigem tarefa/decisão própria.

### 4.5 Gestão de conversas — prioridade média

Propostas:

- PATCH `/api/v1/conversas/{id}` para título (não branco, até 200 caracteres) e status.
- DELETE `/api/v1/conversas/{id}` para exclusão, após definir retenção/soft delete e recibos.
- GET da listagem com `status` e `search` para busca em todo o histórico, mantendo paginação.
- Ordenação determinística por updatedAt e id para reduzir inconsistências entre páginas.

Aplicar owner + tenant em todas as operações. Não conceder leitura/gestão ao administrador
por hierarquia. Definir arquivamento/restauração, se impede envio e estados de corrida.
A prévia impede enviar em arquivadas; essa regra ainda precisa de validação de produto.
Exclusão/arquivamento ampliam o MVP da ADR-39 e exigem revisão formal antes de implementação.

**Implementado (Sprint 4):** `PATCH /api/v1/conversas/{id}` (título/status),
`DELETE` lógico (204) e `GET /api/v1/conversas?status=&q=&page=&size=`. Arquivada não
aceita envio (409 `CONVERSATION_ARCHIVED`); excluída some de todas as leituras (404).
Owner + tenant em todas as operações; `TENANT_ADMIN` continua sem CHAT.

### 4.6 Recuperação de falhas, idempotência e estado da execução — prioridade alta

- Aceitar clientMessageId/idempotency key para não duplicar USER nem cobrança em retry.
- Retornar conversationId, messageId e requestId quando já houver persistência, inclusive
  no erro da primeira pergunta. Hoje a UI só consegue recarregar a lista e orientar o usuário.
- Persistir estado de turno: pendente, concluído, falhou, cancelado ou resultado desconhecido.
- Expor consulta/reconciliação por requestId ou clientMessageId.
- Devolver códigos estáveis de erro além de message, sem conteúdo interno ou segredos.
- Serializar ou controlar envios concorrentes na mesma conversa; a UI bloqueia duplicação
  apenas dentro da página atual, o que não cobre múltiplas abas/dispositivos.
- Rever limite de 8.000 caracteres aplicado também a ASSISTANT: uma resposta maior pode ser
  contabilizada e falhar depois na persistência. Definir política e erro recuperável.
- Tratar falha de persistência da resposta após sucesso do provider sem executar novamente
  a mesma inferência por simples retry do cliente.

**Implementado (Sprint 4, parcial):** os dois POSTs aceitam `clientMessageId`
(8–64, `[A-Za-z0-9_-]`). Repetição com turno concluído devolve 200 com o mesmo turno e
recibo, sem nova tentativa; turno sem ASSISTANT/requestId devolve 409
`TURN_IN_PROGRESS_OR_FAILED` com `messageId` e `requestId` (quando houver). A corrida
em conversa existente é resolvida pelo índice único; no primeiro turno, a checagem é
por `(owner, clientMessageId)` sem índice novo, com a limitação de corrida registrada.
Consulta por `requestId`/`clientMessageId` para reconciliação dedicada, estado
`cancelado`, moeda e política para ASSISTANT > 8.000 continuam pendentes.

### 4.7 Regenerar resposta — prioridade média

Proposta: POST `/api/v1/conversas/{id}/mensagens/{messageId}/regenerar`, com modelo solicitado
e idempotência. Não duplicar a pergunta USER. Definir se preserva versões, ramifica ou substitui,
qual contexto é enviado quando a resposta não é a última e como contabilizar cada tentativa.
A prévia substitui a resposta fictícia em memória, sem estabelecer a regra de retenção real.

### 4.8 Streaming e cancelamento real — evolução

A interface atual mostra espera sem streaming. Para transmissão incremental, definir SSE
ou outro contrato com eventos: início (IDs), delta, conclusão com recibo e erro/cancelamento.
Preservar cookies/BFF, tenant, autorização, desconexão, reconexão e ausência de logs de conteúdo.
O cancelamento deve ser operação backend por execução, com estado final e cobrança conhecida
ou inconclusiva. Abortar fetch no navegador não cancela necessariamente o provider.

### 4.9 Anexos — depende de escopo explícito

Há somente uma interação visual com documento fictício. Para arquivos reais, definir upload,
tipos, tamanhos, limites por conversa, verificação de conteúdo, armazenamento, expiração,
remoção e acesso exclusivo owner/tenant. Retornar estado de processamento e erro por arquivo.
Precisará relacionar attachmentIds ao envio, sem confiar em IDs de outro usuário.
Não implementar RAG, vetores, extração documental ou provider concreto como consequência
automática deste botão; isso exige tarefa explícita e ADR quando aplicável.

## 5. Critérios para concluir a integração futura

- Seletor funciona com usuário colaborador e só oferece modelos permitidos.
- Modelos e recibos antigos sobrevivem a recarga, inclusive após mudança no catálogo.
- Sem acesso cruzado entre usuários/tenants em chat, busca, recibos e anexos.
- Renomear/arquivar/restaurar/excluir confirmam resultado do servidor e reconciliam falhas.
- Cotas e alertas vêm de fonte oficial; zeros não substituem desconhecidos.
- Falha/timeout/retry não duplicam pergunta nem execução; usuário consegue reconciliar.
- Resposta longa e concorrência têm testes proporcionais ao risco.
- Regeneração, retenção, cancelamento e cobrança têm semântica documentada.
- Prévia continua isolada das APIs e dos dados reais.
- Nenhum prompt/resposta/arquivo/credencial é enviado a analytics ou logs.

## 6. Validação e limites da entrega

Executar apenas os testes referentes à implementação do chat:

```sh
node --test src/test/frontend/chat.test.js
```

Os testes cobrem validação, uso desconhecido, contratos de envio, cookie/CSRF/AbortSignal,
erros HTTP, isolamento das fixtures, histórico, seleção de modelo, recibos, regeneração,
renomeação, arquivamento, exclusão e interrupção. A rota conectada exige sessão e a prévia
não carrega o adapter de autenticação. A suíte completa de backend não foi executada,
conforme o escopo desta entrega. O status de CI deve ser verificado no GitHub após o push.

A conferência no navegador incluiu desktop e viewport móvel, modelo por envio, recibo,
regeneração, arquivamento, consumo e falha 502 com reconciliação do histórico.

O fluxo conectado também foi validado com uma conta colaboradora e PostgreSQL real:
criação de conversa, dois envios com resposta ASSISTANT e recuperação das quatro mensagens
após recarga. O provider STUB já existente foi cadastrado pelas APIs administrativas e
vinculado à política do departamento da conta de teste. Não houve alteração de permissões
ou código backend. Recibos completos após recarga continuam pendentes, conforme seção 4.2.

### Reproduzir com o STUB existente

1. Iniciar o backend e o PostgreSQL com o ambiente local configurado; iniciar o frontend.
2. Com administrador do mesmo tenant, cadastrar um provider de kind STUB (ou reutilizar um
   ativo) e um modelo AVAILABLE com externalName `stub-echo` e preços de entrada/saída zero.
3. Usar PUT `/api/v1/ai-gateway/departments/{id}/model-policy` para permitir o modelo e
   defini-lo como padrão do departamento do colaborador. Não substituir política existente
   sem revisar os modelos já permitidos.
4. Entrar com o colaborador em `/chat`, enviar duas mensagens e conferir os recibos.
5. Recarregar e reabrir a conversa para verificar a persistência.

O STUB devolve `[stub]` seguido da última pergunta (até 200 caracteres). Tokens são contagem
simulada de palavras, não medição de uma IA comercial. O custo é zero quando os preços
foram explicitamente cadastrados como zero. Não precisa de chave nem chama serviço externo.
TENANT_ADMIN sozinho não tem CHAT:CREATE/READ. Os testes podem gerar metadados de uso nos
indicadores do tenant. Cadastros locais e conversas de teste permanecem somente no banco
utilizado; cada ambiente deve configurar seu próprio provider/modelo/política.
