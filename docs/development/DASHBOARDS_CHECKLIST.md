# Checklist dos dashboards — SPI-93

## Evolução visual local — SPI-92 / SPI-93 (20/11/2026)

- Cards e superfícies seguem o estudo visual aprovado; CSS restrito a `.dashboard-layout`.
- Os quatro perfis mantêm indicadores, tabelas, metadados de cobertura e permissões.
- A simulação de retorno do colaborador, quando já habilitada no ambiente, aparece em destaque; as premissas permanecem explícitas.
- O gráfico principal oferece geral, todos por modelo e modelo individual; alterna tokens totais, entrada, saída, requisições e custo.
- Comparação por modelo usa somente o catálogo autorizado da fonte e os endpoints existentes, preservando departamento, pessoa, período e `/me` no perfil pessoal. Até três consultas simultâneas; cache somente no recorte atual e cancelamento ao trocar o contexto.
- Datas ausentes e custo desconhecido permanecem lacunas, sem zero inventado. Gráficos têm alternativa textual e tabela de valores exatos.
- Atalhos 7/30/90 dias usam a data final selecionada e reaplicam os filtros existentes.
- Histórico e operacional continuam acessíveis em “Todos os dados de consumo”, sem soma das fontes. Engajamento e orçamento conservam suas janelas próprias.
- Cadastro, onboarding, chat, backend, schema e controles de autorização não foram alterados. O modal financeiro existente conserva os mesmos contratos.
- Validação local: 127 testes frontend e 1.150 testes Java/ArchUnit; `scripts/check --changed` e `scripts/check` concluídos com Java 21 e `MAVEN_ARGS=-Dmaven.clean.skip=true` para preservar o servidor em execução. Conferência autenticada dos quatro perfis em 1440, 768 e 390 px. CI remoto não executado; nenhuma publicação ou commit.

O restante deste documento registra a integração anterior e suas pendências.

Implementação local na `b-evelyn`, 03/11/2026. Integração da apresentação SPI-92/93
com APIs existentes de consumo, engajamento (SPI-144/147) e orçamento (SPI-131).
Sem mocks, mudanças de RBAC, commits ou publicação no GitHub.

`[x]` significa funcionalidade implementada, não existência de registros em todo
usuário/período. Fontes históricas e operacionais nunca são somadas. Consumo
inicia nos últimos 31 dias da cobertura histórica autorizada. Engajamento tem
seletor próprio: adoção usa 30 dias até o corte; Credits usa o mês do corte.

## Implementado / integrado

- [x] Cards de tokens e custo pessoais; gastos da equipe, departamento e empresa no perfil autorizado.
- [x] Fonte e período explícitos; custo histórico estimado USD, com indicação de registros incompletos.
- [x] Custos operacionais/projeções sem presumir BRL: a API não informa a moeda.
- [x] Backend devolve tokens de entrada/saída e custo nas séries temporais, com o mesmo isolamento tenant/usuário/departamento existente.
- [x] Colaborador: gráfico diário de tokens e card de custo registrado.
- [x] Colaborador: remover contagem de funcionários e listas diárias redundantes de requisições; preservar evolução pessoal de tokens e os dados agregados.
- [x] Colaborador: card de AI Credits reutiliza score, mês, fonte e status calculados pelo backend.
- [x] Colaborador: conversas ativas pessoais paginadas (10), título, canal, data e atalho ao Chat.
- [x] Atalhos do colaborador ao Chat e ao gerenciamento/exclusão no Chat existente.
- [x] Líder: card de custo pessoal e custo do recorte da equipe.
- [x] Líder: ativos/inativos e participação na janela aprovada de 30 dias.
- [x] Líder: comparação do custo coletivo em duas janelas móveis completas de 7 dias (exige filtro de pelo menos 14 dias; não são semanas-calendário).
- [x] Líder/gestor: distribuição do número de requisições por modelo.
- [x] Líder: distribuição de custo por membro na fonte operacional, via groupBy=USER autorizado.
- [x] Líder: destaques de treinados sem uso e ativos sem treinamento, sem classificação fictícia de produtividade nem cursos inventados.
- [x] Gestor: adoção da área conectada ao card e evolução diária do custo do recorte autorizado.
- [x] Gestor: orçamento vigente, consumo, disponível e status por departamento autorizado no mês operacional informado.
- [x] Executivo: gasto corporativo e série diária do custo.
- [x] Executivo: adoção global e ativos/inativos no corte (não simula tendência histórica).
- [x] Executivo: benchmarking de custo por departamento e tabela consolidada, em cada fonte separada.
- [x] Tabela corporativa preserva ressalva de preço parcial; adoção por departamento permanece no bloco próprio, com janela independente.
- [x] Blocos existentes de consumo histórico/operacional, licenças ociosas, adoção por departamento, quadrantes e distribuição de Credits preservados.
- [x] Alertas e projeção mensal existentes preservados; projeção simples não é chamada de cenário otimizado entre modelos.
- [x] Indicadores indisponíveis explicam sua dependência; não exibem zero fictício.
- [x] Limpeza de valores durante recarga/erro; respostas antigas não substituem recortes novos nas consultas complementares.
- [x] Usuários/tenants continuam determinados e validados pelo backend; nenhum fallback amplia o recorte.

## Pendências de regra ou fonte (sem números simulados)

- [x] Ranking pessoal: créditos do mesmo departamento, mês, fonte e situação dos dados, com empates.
- [ ] Ranking nominal da equipe.
- [x] Média anônima de tokens: mês fechado, todos os modelos, mesmo departamento e fonte, exclui o ator, requer 10 colegas com uso. Consumo pessoal continua visível separadamente.
- [x] Eficiência em contexto na demo compara horas pessoais e média anônima de horas no departamento, com 30 segundos por requisição e o mesmo recorte mensal da comparação de tokens.
- [x] Tempo e retorno simulados no colaborador em demo local com QSOFT_DASHBOARD_DEMO=true: 30 s/requisição, US$ 15/h, US$ 0,01 de custo simulado por requisição. Nenhuma gravação financeira.
- [ ] Tempo economizado real: validar premissas por interação/tarefa e ajustes de desempenho.
- [ ] Valor gerado: obter custo-hora autorizado, moeda/câmbio e premissas versionadas.
- [ ] Benefício líquido, retorno sobre custo de IA, cobertura e séries de eficiência: dependem das premissas econômicas.
- [ ] ROI de treinamentos: custos e método de atribuição do benefício ausentes.
- [ ] Cenário otimizado, demanda atendida, custos/benefícios projetados e ganho potencial: falta implementação com entradas e restrições aprovadas.
- [ ] Limites individuais e capacidades contratadas: política, valores, unidades e vigências ausentes.
- [ ] Governance Score de área/global e histórico: componentes completos ainda não implementados.
- [ ] Shadow AI/homologação/monitoramento: classificação temporal, inventário e cobertura não integrados.
- [ ] Infraestrutura: não há coleta de CPU/RAM.
- [ ] Critérios específicos de gargalos e recomendações de capacitação: não definidos.
- [x] Executivo: um único card de adoção global para a taxa ativos/elegíveis.

## Pendências de integração / autorização

- [ ] Histórico unificado Chat + Agente: a tabela atual lista conversas do Chat, não interações individuais; falta histórico do agente.
- [ ] Atalho para interface própria do Agente de Políticas ainda não integrado ao dashboard.
- [ ] Distribuição por membro no histórico: API atual não oferece agrupamento por funcionário.
- [x] Comparativo temporal do custo pessoal e coletivo do líder, sem somar séries sobrepostas.
- [ ] Série temporal de adoção: API atual oferece retratos por corte, não série consolidada.
- [ ] Gastos corporativos no gestor: indisponíveis por restrição departamental; não ampliar RBAC.
- [ ] Desempenho por identidade de agente: tentativas Gateway não equivalem a execução por agente.
- [ ] Painel técnico do administrador: permanece redirecionado ao onboarding pelas regras atuais.
- [ ] Logs, erros, latência e coberturas técnicas: metadados de tentativas existem, mas precisam de superfície técnica autorizada e agregações próprias.
- [ ] Eventos históricos de segurança existem, sem API de alertas atuais integrada ao painel técnico.
- [x] Executivo: atalho para a tela de orçamento mensal com DEPARTMENT_BUDGET:MANAGE no próprio tenant.
- [ ] Proposta de orçamento depende de fluxo de aprovação.
- [ ] Exportação PDF/apresentação, BYOK e atalhos administrativos específicos não integrados nesta entrega.

## Verificação

- [x] Testes frontend de ausência vs zero, fontes/moedas, comparação temporal, recarga/erro e restrição corporativa do gestor.
- [x] Teste de integração ampliado para tokens/custo temporais pessoais sob injeção de IDs de colega/tenant.
- [x] Frontend: 96 testes passaram na execução completa, sem falhas ou skips.
- [x] Java: check completo com 880 testes unitários/ArchUnit passou; compilação com release 21 (JDK local 25).
- [x] Check --changed foi tentado; a limpeza padrão falhou por ACL de exclusão em target no Windows/OneDrive. mvn test passou; ./scripts/check passou com MAVEN_ARGS=-Dmaven.clean.skip=true, sem desativar testes ou alterar ACL.
- [x] Conferência local: login/dashboard/histórico/credits 200 para colaborador, líder, gestor e executivo; administrador 302 para onboarding.
- [x] Totais de tokens de entrada das séries históricas conferidos contra os agregados por modelo nos quatro perfis.
- [x] Navegador: cards, fonte histórica/operacional, Credits, histórico pessoal e ausência de erros JS conferidos para colaborador; imagem local target/dashboard-colaborador-spi93.png.
- [ ] Integração Testcontainers: ./scripts/check-integration foi tentado (DashboardMetricsIT, sem clean); bloqueado por Docker Engine desligado. A suíte não foi validada nesta máquina.
- [ ] CI remoto: não executado; nada enviado ao GitHub.

Fontes ausentes continuam explicadas na tela. Nenhuma medição, salário, score,
economia ou autorização foi inventada para preencher os espaços pendentes.
