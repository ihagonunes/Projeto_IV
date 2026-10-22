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
/dashboards/executivo, /dashboards/agentes.

Queries: periodo=semanal|mensal, estado=normal|vazio|erro e, na análise
de agentes, agente=politicas-demo|orcamento-demo. Valores inválidos
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

Líder, Gestor e Executivo possuem apenas estados de escopo pendente.
Não há matriz de visibilidade inventada, hierarquia implícita ou acesso organizacional.
Equipe, área e departamento são termos para a mesma unidade organizacional.
Não foram criadas três estruturas.

Os atalhos /chat, /agente-politicas e /chat/historico são destinos propostos,
ainda sem implementação no roteador. Podem retornar 404.
O histórico dentro da página contém apenas interações próprias simuladas.
Não implementa exclusão, arquivamento, dashboard administrativo ou Recibo White-Box.
Permissão administrativa será independente da hierarquia: futuramente habilitará
o dashboard administrativo além do dashboard hierárquico, sem substituir este último.

## Contrato e integração futura

routes/dash.routes.js -> modules/dashboard/dashboards.controller.js
-> dashboards.provider.js -> mocks -> dashboard/views/prototype.ejs.

O provedor assíncrono retorna:
- meta: simulated, version, timeZone, referenceDay, usdBrl;
- profile, title, period (key/start/end/label), state, pending;
- agents e selectedAgent (somente dados de agentes quando aplicável);
- data: kpis, timeline, stages/executions ou history, conforme a visão.

Substituir o provedor por chamadas à instância Axios de config/api.js somente após
definição dos endpoints e contexto autenticado. Não enviar user/tenant arbitrários
da query. Java deverá entregar todos os indicadores/séries, escopo e lista de agentes
já autorizados; não usar fixtures como fallback para falhas reais.
A navegação de desenvolvimento deverá ser substituída pelos destinos permitidos
pelo backend, preservando o contrato de apresentação e o tema existente.

Pendências: matriz superior, endpoints/DTOs definitivos, período configurável,
critérios de ranking/percentis, privacidade dos agregados, fonte de câmbio,
disponibilidade dos agentes e destinos reais de Chat/histórico.

## Verificação

node --test src/test/frontend/dashboards.test.js

Testa consistência da massa, períodos, fuso, percentis, custo diário, filtro de agente,
anonimização do payload, vazio/erro, renderização EJS, parâmetros inválidos e produção.
Revisar desktop e celular, filtros, paginação, acesso via teclado e falha da CDN.
As verificações Java do repositório continuam sendo scripts/check --changed e scripts/check.
