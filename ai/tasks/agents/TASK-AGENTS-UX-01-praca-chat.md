# SPI-180 — Praça de agentes e envio pelo chat

## Pedido de Vulpi — 23/11/2026

Redesenhar a praça preservando os personagens e a identidade do ATLAS.
A praça serve para montar e confirmar o fluxo. Depois da confirmação, salvar
e publicar pela API existente, fechar a praça e focar o compositor do chat.
O prompt e o resultado ficam no chat. Esta solicitação substitui a seção de
execução dentro da praça descrita no BE-07, critério A.

## Comportamento

- Biblioteca separada, agentes disponíveis primeiro, DRAFT identificado e bloqueado.
- Canvas com conexões, organização automática e interação por teclado.
- Confirmar não executa nenhum agente: somente cria/salva, valida e publica.
- Falha ou conflito de publicação mantém a praça aberta com orientação.
- Confirmação entrega id, nome e grafo ao controlador do chat.
- O chat mostra o fluxo selecionado e usa o compositor para enviar a tarefa.
- O envio explícito chama `/agentes/fluxos/{id}/execucoes`, com chave de
  idempotência nova e bloqueio de envio concorrente. Não altera o payload de
  `/conversas`, não cria `agentFlowId` nesse contrato e não muda RBAC.
- Resultados são apresentados como texto seguro, com nomes e estados das etapas.
  Não se acrescenta persistência de respostas de agentes ao histórico do chat.
- Prévia isolada: confirmação e resultado demonstrativo não consultam modelos.
- Fluxos publicados podem voltar ao chat, mas não são editados no lugar.
- Novo fluxo permite começar outra composição. Novo chat remove o fluxo ativo.

## Design e validação

Referências solicitadas: Leonxlnx/taste-skill, tmustier/web-design-guidelines e
VoltAgent/awesome-design-md (Linear). Aplicação dos princípios de hierarquia,
superfícies escuras, contraste, espaçamento, foco visível e adaptação ao celular;
sem substituir o stack Express/EJS ou adicionar dependências.

Validação automatizada usa clientes falsos. Inspeção visual usa `/chat/previa`
em Chrome headless. Nenhuma geração de IA ou execução paga deve ser realizada.
Checks: `node --test src/test/frontend/*.test.js`; não há mudança de backend,
schema ou fronteiras modulares, portanto os checks Maven e integração não se aplicam.

## Correção de resposta vazia — SPI-180

Após uma execução real iniciada por Vulpi, o DeepSeek V4.1 Flash 1017 atingiu
o teto de 2048 tokens e o fluxo foi marcado como concluído sem texto final.
O frontend interpretava o texto vazio como erro financeiro.

- O adapter OpenAI-compatible envia `thinking: {type: "disabled"}` somente
  para `deepseek-v4.1-flash`, conforme a documentação do modelo. O teto de saída
  e a reserva financeira permanecem em 2048 tokens; não há retry automático.
- Um agente que responde vazio interrompe o fluxo com `AGENT_EMPTY_RESPONSE`.
  O requestId permanece na etapa para não perder o recibo e o custo da chamada.
- A interface distingue resultado ausente de falha financeira, inclusive para
  respostas de execuções anteriores que vierem como `SUCCEEDED` sem texto.
- Não exibir `reasoning_content` como resposta nem gravá-lo em logs ou banco.
- Verificação restrita aos testes dos componentes alterados: adapter com HTTP
  local simulado, executor com gateway falso e apresentação frontend.
- Referência: https://api-docs.deepseek.com/guides/thinking_mode/
