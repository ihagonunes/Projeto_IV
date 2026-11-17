# Praça dos agentes — SPI-136

Editor visual da interface do chat, conforme escopo solicitado para a praça.
Abra **Agentes** no chat. A área começa com **Tarefa do usuário**.
A barra lateral contém 18 agentes de exemplo em uma lista vertical com busca
e categorias. Use **Recolher agentes** para ampliar a área do fluxo e
**Mostrar agentes** para reabrir a lista.

- Arraste um agente para a área pontilhada ou clique no cartão para adicioná-lo.
- Arraste etapas existentes para reposicionar. Com foco no cartão, as setas também movem a etapa.
- Clique em **Saída** e depois em **Entrada** para conectar. Repita para criar ramos paralelos.
- Adicione o **Consolidador** e conecte as entradas dos ramos a ele.
- **Adicionar resultado** cria uma única etapa final, que deve ser conectada à saída desejada.
- Remova etapas pelo × e conexões com um clique duplo na seta. Pelo teclado,
  selecione a conexão com Tab e pressione Enter ou Delete.
- Ciclos e conexões duplicadas são rejeitados.

O rascunho existe somente em memória nesta página: fechar e reabrir a praça
preserva o desenho; recarregar o apaga. Não há execução, consulta a documentos,
acesso adicional a dados, persistência ou alteração no envio do chat.
Os cartões descrevem habilidades propostas, não integrações disponíveis.

Validação: `node --test src/test/frontend/agent-plaza.test.js src/test/frontend/chat.test.js`.
Conferência visual: montar dois ramos saindo da tarefa, uni-los no consolidador,
adicionar resultado, remover uma conexão e reabrir a praça.
