# Dashboards de desenvolvimento

Implementação frontend autorizada diretamente pelo usuário, sem tarefa Jira.
Nenhum arquivo Java, migration, autenticação ou API real foi alterado.

## Executar

Na raiz: npm ci e npm start. Acesse http://127.0.0.1:3000/dashboards.
Não requer Spring Boot nem banco para estas páginas.
A rota /dashboard continua sendo o dashboard integrado existente.
As rotas /dashboards são desativadas quando NODE_ENV=production.
Não são autorização: exibem exclusivamente fixtures públicas de desenvolvimento.

Rotas: /dashboards/colaborador, /dashboards/lider, /dashboards/gestor,
/dashboards/executivo, /dashboards/agentes e /dashboards/governanca.

Queries: periodo=semanal|mensal, estado=normal|vazio|erro e, na análise
de agentes, agente=politicas-demo|orcamento-demo. Gestor aceita `limite`
apenas para pré-visualização local da resposta. Valores inválidos
usam a seleção padrão. Cenário de erro retorna HTTP 503 e permite tentar novamente.
Filtros são GET e funcionam mesmo sem JavaScript; o botão Aplicar atualiza ambos os gráficos.
Com JavaScript, a tabela é paginada em dez linhas. Sem JavaScript, todas ficam acessíveis.
Chart.js é carregado por CDN fixada em 4.4.8, como os assets Bootstrap existentes.
Se a CDN falhar, os dados tabulares dos gráficos continuam disponíveis.

## Dados e limites

90 execuções determinísticas de 01 a 09/10/2026; referência de hoje fixada
em 09/10/2026. Não dependem do relógio do computador. O período semanal é
04 a 10/10 (domingo a sábado); mensal é outubro. Datas são agrupadas em
America/Sao_Paulo. Dias sem execuções têm volume zero e latência ausente.
O custo diário usa sempre 09/10, inclusive no modo mensal, respeitando o filtro de agente.
O modo vazio simula ausência na fonte inteira, inclusive no dia de referência.
A moeda principal é BRL; USD_BRL_DEMO=5.15 é apenas referência temporária.
p50/p95 usam nearest-rank somente na preparação das fixtures, não como regra aprovada.
Cálculos demonstrativos permanecem nos mocks; controller e views não consolidam métricas.
Nomes de modelos e sessões são fictícios. A lista de agentes é uma amostra,
não uma regra de disponibilidade por usuário.
As latências RAG, tool e LLM não cobrem necessariamente toda a latência total.
A tabela permite comparar valores exatos sem depender das cores/gráficos.

Colaborador recebe somente seu conjunto individual e médias agregadas:
nenhum registro gerencial ou identificador de terceiro é enviado à página.
AI Credits, ranking e médias são fixtures; não definem critérios de classificação.
Não há grupos pequenos ou detalhamento individual para reidentificar pessoas.

Líder, Gestor, Executivo e Governança/TI possuem painéis ilustrativos adicionais.
`mocks/organizational.mock.js` prepara a amostra comum de uso para os três primeiros,
e tentativas/infraestrutura fictícias para Governança/TI. Totais, gráficos e tabelas
derivam da mesma amostra de cada painel. O período mensal mostra 01–09/10 contra
01–09/09; o semanal compara 04–09/10 com 27/09–02/10, ambos com seis dias observados.
Score departamental/global e ROI são valores de apresentação, sem cálculo oficial.
O ROI ilustrativo usa benefício estimado de R$ 1.800 e investimento de R$ 1.200.
O orçamento usa um limite mensal fictício; o custo mostrado é o do período selecionado.
O formulário de orçamento recebe `limite` somente para pré-visualizar saldo na resposta
atual; recarregar sem o parâmetro restaura o valor da fixture. Não há gravação.
O relatório executivo é uma tabela
demonstrativa sem exportação. Os atalhos de Governança/TI levam a páginas existentes,
que aplicam suas próprias permissões; o menu entre protótipos não controla acesso.
Não há matriz de visibilidade real, hierarquia implícita ou acesso organizacional.

Os atalhos /chat, /agente-politicas e /chat/historico são destinos propostos,
ainda sem implementação no roteador. Podem retornar 404.
O histórico dentro da página contém apenas interações próprias simuladas.
Não implementa exclusão, arquivamento ou Recibo White-Box. O painel demonstrativo
de Governança/TI não consulta logs reais nem substitui as áreas administrativas.

## Contrato e integração futura

routes/dash.routes.js -> modules/dashboard/dashboards.controller.js
-> dashboards.provider.js -> mocks -> dashboard/views/prototype.ejs.
Os quatro novos painéis usam a parcial `dashboard/views/management.ejs` e
`public/js/pages/management-dashboards.js` para desenhar gráficos prontos.

O provedor assíncrono retorna:
- meta: simulated, version, timeZone, referenceDay, usdBrl;
- profile, title, period (key/start/end/label), state, pending;
- agents e selectedAgent (somente dados de agentes quando aplicável);
- data: kpis, timeline, stages/executions ou history, conforme a visão.

Qualquer integração futura requer definição de endpoints, escopo e contexto autenticado.
Não enviar user/tenant arbitrários da query. Java deverá entregar indicadores/séries,
escopo e lista de agentes já autorizados; não usar fixtures como fallback para falhas reais.
A navegação de desenvolvimento deverá ser substituída pelos destinos permitidos
pelo backend, preservando o contrato de apresentação e o tema existente.

Pendências: matriz superior, endpoints/DTOs definitivos, período configurável,
critérios de ranking/percentis, privacidade dos agregados, fonte de câmbio,
disponibilidade dos agentes e destinos reais de Chat/histórico.

## Verificação

node --test src/test/frontend/dashboards.test.js src/test/frontend/organizational-dashboards.test.js

Testa consistência da massa, períodos, fuso, percentis, custo diário, filtro de agente,
anonimização do payload, vazio/erro, renderização EJS, parâmetros inválidos e produção.
Revisar desktop e celular, filtros, paginação, acesso via teclado e falha da CDN.
As verificações Java do repositório continuam sendo scripts/check --changed e scripts/check.
