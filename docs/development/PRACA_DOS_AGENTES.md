# Praça dos agentes — SPI-136

> **Status:** protótipo visual local. Persistência, catálogo real e execução
> estão planejados em
> [AGENTES_PIPELINE_IMPLEMENTACAO.md](AGENTES_PIPELINE_IMPLEMENTACAO.md)
> (ADR-44/45) e rastreados em **SPI-180**. O produto é desktop; mobile não é
> requisito.

Editor visual da interface do chat, conforme escopo solicitado para a praça.
Abra **Agentes** no chat. A área começa com **Tarefa do usuário**.
A barra lateral contém 18 agentes de exemplo em uma lista vertical com busca
e categorias. Use **Recolher agentes** para ampliar a área do fluxo e
**Mostrar agentes** para reabrir a lista.

- Arraste um agente para o cenário ou clique no robô no catálogo para adicioná-lo.
- Os 18 agentes usam os PNGs de 48 × 48 fornecidos por Vulpi, preservados sem
  suavização. O nome fica abaixo dos pés; a descrição aparece em um balão acima
  da cabeça ao passar o mouse ou focar o agente pelo teclado, na praça e no catálogo.
- O fundo usa quatro PNGs de 1536 × 640, em loop de 750 ms por frame,
  com uma camada preta de 33% de opacidade para reduzir o destaque.
  A preferência de movimento reduzido mantém o primeiro frame estático.
  A arte cobre a área do fluxo, sem distorção, com recorte conforme a janela.
  Tarefa do usuário e Resultado usam os robôs 19 e 20, com nome abaixo dos pés
  e descrição no balão. Permanecem etapas de início e fim, fora do catálogo dos
  18 agentes: a tarefa não pode ser removida e só pode existir um Resultado.
- Arraste etapas existentes para reposicionar. Com foco no cartão, as setas também movem a etapa.
- Clique primeiro na etapa de origem e depois na etapa de destino para conectar.
  Repita os pares para criar ramos paralelos. A origem selecionada ganha um brilho
  no robô, sem contorno retangular. Pelo teclado, Enter ou Espaço selecionam as etapas.
- Esc desmarca a origem selecionada e cancela cliques pendentes. Sem seleção,
  não faz nada. Esc nunca fecha a praça; o botão de fechar e a confirmação válida
  continuam fechando a janela normalmente.
- Adicione o **Consolidador** e conecte as entradas dos ramos a ele.
- **Adicionar resultado** cria uma única etapa final, que deve ser conectada à saída desejada.
- **Confirmar esteira** valida agentes conectados, resultado e consolidação dos
  ramos. A confirmação vale nesta página; alterar etapas ou conexões exige nova confirmação.
  Sem Resultado, exibe um aviso dentro da praça, no estilo do aviso de consolidação,
  com um botão para adicionar Resultado. Ao confirmar uma esteira válida, fecha a praça
  automaticamente e mantém o desenho em memória para reabrir.
- Ramos paralelos sem consolidação exibem um aviso e **Adicionar Consolidador e
  ligar ramos**. O botão insere os consolidadores necessários e religa as pontas,
  preservando o resultado e as etapas posteriores. Se ainda não houver resultado,
  adicione-o e conecte a saída final antes de confirmar.
- Remova etapas com um clique duplo no agente, e conexões com um clique duplo
  na seta. O clique duplo não cria uma conexão. Pelo teclado, Delete remove a etapa;
  na conexão, Enter ou Delete removem a seta. A tarefa do usuário não pode ser removida.
- Ciclos e conexões duplicadas são rejeitados.

O rascunho existe somente em memória nesta página: fechar e reabrir a praça
preserva o desenho; recarregar o apaga. Não há execução, consulta a documentos,
acesso adicional a dados, persistência ou alteração no envio do chat. Confirmar
a esteira valida apenas o desenho; não inicia execução nem chamadas de IA.
Os cartões descrevem habilidades propostas, não integrações disponíveis.

Validação: `node --test src/test/frontend/agent-plaza.test.js src/test/frontend/chat.test.js`.
Conferência visual: montar dois ramos saindo da tarefa, uni-los no consolidador,
adicionar resultado, remover uma conexão e reabrir a praça.
