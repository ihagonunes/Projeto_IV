# Indicadores dos dashboards ATLAS: cálculos e fontes de dados

## 1. Escopo, fontes e estado

Etapa de apresentação: SPI-92 / POPROJ-53. Este documento especifica os cálculos
futuros e suas lacunas. Nesta entrega foram alterados os espaços dos dashboards;
não foram implementados cálculos Java, migrations, solver ou consultas novas.

> **Atualização Sprint 4 (01/11/2026):** adoção de 30 dias, cruzamento treinamento × uso
> e AI Credits (`credits-v1`) foram implementados no módulo `engagement` (V023), com
> endpoints em `/api/v1/engajamento/*`. A projeção de orçamento (`projection-v1`) foi
> implementada no `observability`. O restante deste documento continua sendo proposta
> ou pendência.

Inventário verificado nas migrations V002, V009 a V014, no domínio UsageRecord,
no cálculo Model.estimatedCost e no HistoricalUsageAdapter do repositório.
Não houve consulta às linhas do banco em execução: schema declarado não prova
que a tabela esteja populada ou que exista API para o indicador.

Referência econômica: Entrega1_Modelagem_PO_QSOFT (1).pdf, seções 4 a 8 e 9.2.
Decisões vigentes: ADR-13, ADR-17 a ADR-23, ADR-26 e ADR-27. O PDF não substitui ADRs.

Classificação das regras:
- **Existente:** consta no código ou em ADR (não implica implementação completa).
- **Proposta:** fórmula/estrutura futura documentada, ainda sem implementação.
- **Pendente:** falta fonte, parâmetro, contrato ou decisão; não inventar valor.

Naturezas distintas na interface/API:
1. **Observado:** uso registrado. Custo do Gateway é estimado, não faturado.
2. **Estimado:** valor econômico sobre uso observado e premissas versionadas.
3. **Projetado:** resultado de otimização, nunca economia realizada.

## 2. Convenções e proteção dos dados

- Java calcula e valida. Node intermedeia via Axios. EJS/Bootstrap apresentam.
- Tenant vem da autenticação; SQL operacional filtra tenant_id. Histórico filtra
  company_id mapeado ao tenant. CompanyMapping é opcional no loader: validar o
  mapeamento, não assumir que C01 equivale ao UUID do tenant.
- Aplicar SELF/DEPARTMENT/TENANT antes de agregar. URL/cookie não autoriza.
  Gastos corporativos do gestor exigem reconciliar o RBAC atual (departamento).
- Períodos propostos: [inicio,fim), UTC com calendário America/Sao_Paulo. Semana
  domingo-sábado; comparação com mesma duração e corte. Semana parcial compara
  fração equivalente da anterior ou explicita a diferença.
- Histórico e operação são universos separados. Não somar sem correspondência
  de identidades, cobertura temporal e deduplicação. IDs textuais não são UUIDs.
- Dinheiro: BigDecimal/numeric, moeda, vigência e câmbio explícitos. V012/V013 e
  monthly_budget não declaram moeda: não presumir BRL ou USD.
- Sem fonte/parâmetro: null + motivo, não zero fictício. Exceção: ADR-21 determina
  score zero com INSUFFICIENT_DATA para dimensão sem evidência.
- Contagens podem ser zero com cobertura confirmada. Divisor zero retorna N/A;
  não Infinity/NaN. Calcular sobre valores brutos e só arredondar a apresentação.
- DTOs: período, corte, escopo, unidade/moeda, natureza, sourceKind, cobertura,
  dataStatus, calculatedAt e calculationVersion. Não enviar salários ou conteúdo.
- Média anônima exige grupo comparável e tamanho mínimo k. Ambos são pendentes
  de política: enquanto não definidos, suprimir comparações reidentificáveis.

## 3. Tabelas e campos existentes

| Tabela | Campos relevantes verificados | Limite |
|---|---|---|
| app_user | id, tenant_id, department_id, hierarchy_level, status, created_at, updated_at | Sem salário/cargo profissional/senioridade; hierarchy_level não concede permissão por si só |
| department_departments | id, tenant_id, name, leader_user_id, monthly_budget, status | Orçamento atual sem moeda nem histórico mensal |
| department_model_policies | department_id, tenant_id, default_model_id, created_at, updated_at | Política atual; não reconstrói permissões históricas |
| department_model_policy_allowed_models | department_id, model_id | Itens da política; tenant deve ser validado pela política proprietária |
| aigateway_models | id, tenant_id, provider_id, status, input_price_per_1k, output_price_per_1k | Sem moeda, vigência de preço ou cota contratada |
| aigateway_providers | id, tenant_id, name, status | Sem telemetria de infraestrutura |
| observability_usage_records | request_id, tenant_id, department_id, user_id, model_id, provider_id, input_tokens, output_tokens, estimated_cost, latency_ms, status, created_at | AIRequestCompleted é a única origem; imutável/idempotente. Não tem agent_id, ai_tool_id, moeda ou tempo economizado. Não cobre necessariamente falhas/tentativas |
| historical.employees | employee_id, company_id, department_id, job_role, seniority, hire_date, cost_center, active | Dataset sintético sem salário nem histórico de status |
| historical.departments | department_id, company_id, department_name | Sem cost_center |
| historical.ai_usage_logs | usage_id, timestamp, employee_id, company_id, department_id, ai_tool_id, model_id, application, request_count, input_tokens, output_tokens, latency_ms, request_status, project_id, cost_center | Datas textuais; validar semântica de totais e médias por linha |
| historical.ai_models | model_id, ai_tool_id, input_price_usd_per_1k_tokens, output_price_usd_per_1k_tokens | Sem vigência; catálogo sem company_id não autoriza leitura de outro tenant |
| historical.ai_tools | ai_tool_id, licensing_type, tool_category | Separar cobrança por token/assento após mapear os valores |
| historical.ai_billing | billing_id, company_id, billing_month, provider, ai_tool_id, model_id, cost_center, usage_quantity, total_cost, currency | Faturamento, não custo adicional ao estimado do mesmo uso |
| historical.ai_budgets | company_id, cost_center, year, month, budget_amount | Sem moeda e sem department_id |
| historical.ai_licenses | company_id, employee_id, ai_tool_id, assigned_date, expiration_date, license_status | Sem preço da licença/histórico completo |
| historical.training_records | training_id, employee_id, company_id, course_name, course_category, enrollment_date, completion_date, completion_status, score, duration_hours | Sem custo ou evidência de efeito causal |
| historical.approved_ai_tools | company_id, ai_tool_id, approval_status, approval_date, allowed_departments | Aprovação por ferramenta; validar escopo e vigência |
| historical.security_events | event_id, company_id, employee_id, timestamp, ai_tool_id, event_type, severity, policy_id, action_taken | Sem request_id/department_id; não prova todo uso externo |

V011 também contém a política de modelos por departamento; obter elegibilidade
pelo contrato público do módulo, não por join operacional entre módulos.
O histórico é o schema SQL `historical`. Engagement e GovernanceScore têm apenas
pastas/.gitkeep: seus fatos e cálculos operacionais ainda não estão implementados.
Campos CSV citados pelo PDF correspondem às fontes históricas, não às entidades
operacionais automaticamente.

## 4. Consumo, FinOps e operação

R = registros do período e escopo autorizados. U = usuários elegíveis via IAM.
No histórico, normalizar datas/status e deduplicar chaves da fonte antes de agregar.

| Indicador | Fórmula | Fonte / condição |
|---|---|---|
| Requisições N | COUNT DISTINCT request_id | Operacional já implementado. Histórico: SUM(request_count), não COUNT de linhas |
| Tokens | Tin=SUM(input_tokens); Tout=SUM(output_tokens); total=Tin+Tout | R. Histórico: confirmar que tokens já são totais da linha; não multiplicar por request_count sem validar |
| Custo registrado C | SUM(estimated_cost) | R; preservar custo calculado pelo Gateway |
| Custo por interação no Gateway | (Tin_r*Pin+Tout_r*Pout)/1000 | Model.estimatedCost existente; preço por 1k, arredondamento do domínio. Não reprecificar passado com catálogo atual |
| Custo histórico estimado | SUM((Tin_l*Pin_m+Tout_l*Pout_m)/1000) | Uso + catálogo histórico; preços USD e cobrança por token; não é faturamento |
| Custo faturado | SUM(ai_billing.total_cost) por currency/mês | Não somar ao custo estimado do mesmo consumo; conciliar diferenças |
| Custo médio por requisição | C/N | Mesma moeda, universo e cobertura; N=0: N/A |
| Ativos no período | COUNT DISTINCT user_id com uso | API atual; histórico employee_id com request_count>0 |
| Adoção 30 dias | 100*ativos elegíveis nos 30 dias/elegíveis no corte | ADR-18/ADR-40. Implementado no `engagement` (Sprint 4). Diferente de ativos no filtro arbitrário; elegíveis IAM |
| Inativos de uso | elegíveis-ativos; taxa=100*inativos/elegíveis | Não confundir com conta INACTIVE; sem denominador: N/A. Implementado no `engagement` (Sprint 4) |
| Média anônima de tokens | tokens do grupo comparável/número de elegíveis do grupo | Proposta: inclui elegíveis sem uso. Exige política k/grupo e anonimização |
| Gastos próprios/equipe/área/empresa | C em cada escopo | Próprio já pode estar no agregado da equipe: não somar novamente |
| Comparativo temporal | delta=A-B; variação%=100*(A-B)/B | Se B=0, percentual N/A; manter delta e períodos equivalentes |
| Distribuição por modelo | N_m, T_m, C_m; participação=100*N_m/N | Identificar se gráfico usa requisições, tokens ou custo |
| Uso por membro | N_e, T_e, C_e e participação na equipe | Somente membros autorizados, sem salários ou conversas |
| Orçamento disponível | B-C; execução%=100*C/B | monthly_budget atual, mês e moeda correspondentes. B ausente/<=0: razão N/A; saldo negativo não deve ser ocultado |
| Sucesso técnico | 100*N_sucesso/N_tentativas | Falta universo de todas tentativas na operação. Histórico soma request_count após mapear request_status |
| Taxa de erros | 100*N_erros/N_tentativas | Mesma lacuna; não deduzir que ausência de falha em UsageRecord é 100% sucesso |
| Latência média | SUM(latency_ms válidos)/COUNT(latências válidas) | Histórico ponderado por request_count só se latência for média da linha |
| p50/p95 | latência na posição ceil(p*n), ordenada | Proposta nearest-rank sobre amostras individuais; médias históricas não permitem percentil real das requisições |
| Alertas de segurança | COUNT DISTINCT event_id por período/tipo/severidade | historical.security_events; falta equivalente operacional |
| Logs de auditoria | Listagem paginada de metadados | Não é cálculo. Sem tokens de autenticação/conteúdo/segredos |
| Infraestrutura | CPU%=100*tempoCPU/capacidadeCPU; memória%=100*bytesUsados/limiteBytes | Proposta; janela/unidade explícitas e telemetria ainda inexistente; tokens não medem CPU/RAM |
| Agentes da área | execuções, sucesso, latência, custo por agent_id | Falta execução operacional vinculada ao uso; application não é agent_id |

Lacuna resolvida na Sprint 4: preço ausente não vira mais zero. Um modelo sem preço
produz `costStatus=INCONCLUSIVE` (ADR-38/S4-D12), sem custo e sem `UsageRecord`;
zero explícito continua sendo preço válido. O preço usado é fotografado na tentativa.
`UsageRecord.created_at` usa `Instant.now()` no consumo do evento: é data de registro,
não necessariamente data exata de execução. Futuro contrato deve separar horários.

A projeção determinística de orçamento (`projection-v1`) foi implementada no
`observability` (Sprint 4): média móvel do custo conhecido, projeção até o fim do mês
e dia de esgotamento, sem LLM. Ver `DASHBOARD_E_ORCAMENTOS.md`.

Orçamento histórico: mapear cost_center a departamento/empresa via employees e
uso. Validar cardinalidade e vigência para não multiplicar orçamento por funcionário
num join. Ausência de correspondência não vira orçamento zero. Licenças por assento
estão fora da otimização por token do PDF, mas entram no custo corporativo quando
faturamento existir, sem dupla contagem.

## 5. Produtividade e valor econômico estimados (proposta)

Para funcionário e, modelo m e período:
- H_e = custo-hora carregado, em moeda/hora, autorizado e com vigência.
- t_e = minutos economizados por interação, premissa validada por tarefa/perfil.
- s_m = fração de sucesso técnico [0,1]; L_m = latência média válida em ms.
- N_em = requisições observadas cobertas; C_em = custo correspondente.

### 5.1 Reprodução acadêmica, sem criar salários fictícios em produção

SalarioMock_e = BaseSenioridade*MultiplicadorCargo*(1+min(0.01*anosCompletos,0.15)).
H_e_BRL = SalarioMock_e*1.7/220.
H_e_USD = H_e_BRL/FX_BRL_por_USD.
ValorBase_e = H_e*(t_e/60).

O PDF usa t=3, FX=5 e bases 4500/7500/11000/14000/18000 para
Junior/Pleno/Senior/Especialista/Gerente. São exemplos, não fatos de produção.
A tabela completa de multiplicadores das 12 famílias não consta no PDF: pendente,
não inventar. Cargo/senioridade/hire_date existem só em historical.employees.
Não confundir hierarchy_level de app_user com faixa salarial.

### 5.2 Desempenho operacional do modelo

v_m = 1-(L_m-L_min)/(L_max-L_min).
q_m = 0.30*s_m+0.70*v_m.

Conjunto de modelos, janela de referência, versão e amostra mínima precisam ser
identificados. Usar s=0.95 para 95%, não 95. Se L_max=L_min, faltam dados ou status
não está mapeado: q indisponível até definir política explícita. Não dividir por zero.
Mudança no conjunto de referência altera q: versionar. q não é qualidade da tarefa.

### 5.3 Indicadores sobre o uso observado

TempoEconomizadoEstimado_h = SUM_em(N_em*t_e/60*q_m).
ValorGeradoEstimado = SUM_em(N_em*H_e*t_e/60*q_m).
BeneficioLiquidoEstimado = ValorGeradoEstimado-SUM_em(C_em).
RetornoEstimadoSobreCustoIA_pct = 100*BeneficioLiquidoEstimado/SUM_em(C_em).

q já incorpora sucesso: não multiplicar novamente por s. A soma aplica a lógica
do PDF ao uso observado; inclusive o tempo continua estimado. Se houver tempo
medido por tarefa futuramente, criar outra versão e evitar atribuir a economia
da mesma tarefa a cada requisição que ela gerou.

CoberturaEstimativas_pct = 100*requisições com custo/moeda/H/t/q válidos/N_total.
Exibir também pessoas cobertas/elegíveis e custo coberto/custo total. Benefício
desconhecido não é zero. Custos sem estimativa de benefício permanecem no custo
total: retorno do subconjunto coberto não pode ser anunciado como retorno global.
Retorno global exige cobertura completa ou metodologia explícita para a lacuna.

Benefício representa valor potencial do tempo, não receita ou lucro contábil.
ROI completo incluiria licenças/implantação/operação atribuíveis; sem essas fontes,
usar o nome limitado “Retorno estimado sobre custo de IA”. Custo zero torna a razão N/A.
Benchmark anônimo exige tarefa/perfil comparável, mesma cobertura/versão e política
de grupo mínimo. Não ranquear por salário, lucro absoluto ou quantidade de tokens.

## 6. Otimização: projeção, não resultado realizado (proposta)

x_em = requisições mensais projetadas do funcionário e no modelo m.
c_em = (mediaTokensEntrada_e*precoEntrada_m+mediaTokensSaida_e*precoSaida_m)/1000.
b_em = H_e*t_e/60*q_m-c_em.
Maximizar Z = SUM_em(b_em*x_em).

Restrições:
- SUM_m x_em=demanda_e, para cada funcionário.
- SUM_m c_em*x_em<=limiteIndividual_e, se política individual for adotada.
- SUM_e_do_departamento,m c_em*x_em<=orcamentoDepartamento.
- SUM_e x_em<=cotaMensal_m.
- x_em>=0; x_em=0 para modelos não elegíveis àquele funcionário/área.

Elegibilidade exige homologação, política do departamento e modelo/provider
disponíveis; não basta preço baixo. Cota mensal não substitui rate limits por minuto.
Janela de demanda deve ser explícita; não fixar os 14 meses acadêmicos em produção.
Valores fracionários servem para planejamento médio (Simplex), não roteamento
discreto. Alocação inteira/arredondamento exige revalidar todas as restrições.

Alternativa do PDF: limiteIndividual=B_dep*salario_e/SUM_salarios_dep. Não foi
adotada como política, não é AI Credits e não bloqueia acesso. Salário, demanda ou
híbrido continuam pendentes de decisão formal (o próprio PDF registra o trade-off).

Indicadores do cenário:
- CustoProjetado=SUM(c_em*x_em).
- ValorGeradoProjetado=SUM(H_e*t_e/60*q_m*x_em).
- BeneficioLiquidoProjetado=ValorGeradoProjetado-CustoProjetado.
- GanhoPotencial=BeneficioLiquidoProjetado-BeneficioLiquidoReferencia.
- VariacaoProjetadaCusto=CustoProjetado-CustoReferencia;
  variação%=100*(CustoProjetado-CustoReferencia)/CustoReferencia.
- DemandaAtendida%=100*SUM(x_em)/SUM(demanda_e), verificar também cada pessoa.
- FolgaIndividual=limiteIndividual_e-SUM_m(c_em*x_em).
- FolgaDepartamento=B_dep-SUM_e,m(c_em*x_em).
- FolgaModelo=cota_m-SUM_e(x_em).

Referência e otimização: mesma demanda, preços, moeda, método e janela. Ganho
potencial pode envolver custo maior com benefício maior: não chamar de economia.
“LucroRealizado” da seção 8 do PDF usa x*: trata-se de benefício projetado.
Resultado tem estado OPTIMAL/FEASIBLE/INFEASIBLE/INSUFFICIENT_DATA e, se aplicável,
gap de optimalidade. Sem cotas/demanda/parâmetros não há cenário ótimo publicável.

Esta etapa só reserva a apresentação. ai/project.md exclui roteamento por custo/
latência no MVP. Solver, dependência, ownership e eventual mudança de roteamento
exigem task/ADR próprios. Não adicionar Python/PuLP ao runtime por causa do PDF.

## 7. AI Credits e Governance Score

AI Credits permanece engajamento [0,100], nunca saldo financeiro (ADR-19).
Com U,T,L normalizados em [0,100]: Credits=0.50*U+0.30*T+0.20*L.
Licença NOT_APPLICABLE: Credits=(0.50*U+0.30*T)/0.80. Falta de dado não é N/A.

**Implementado na Sprint 4 (`credits-v1`, ADR-40):** U=100*min(dias ativos no mês/10,1);
T=100*min(conclusões até o fim do mês,1); L=100*licenças utilizadas/licenças aplicáveis,
com `NOT_APPLICABLE` normalizado por 0,8. Score=0,5U+0,3T+0,2L, 2 casas HALF_UP.
Mês fechado usa snapshot imutável; mês corrente é `PROVISORIO`. Metas, ranking e a
visão de equipe continuam pendentes (Sprint 5). Dados: uso Observability; fatos de
licença/treinamento Engagement (V023); elegíveis IAM. Não substituir por lucro.

Ranking proposto=1+quantidade de elegíveis com Credits estritamente maior.
Mesma população, período, versão/cobertura; empates compartilham posição. IDs
só desempatem ordenação visual. Colaborador vê sua posição; líder apenas equipe.

ADR-20: Score=0.40*Governance+0.20*Adoption+0.20*Training+0.20*Utilization.
Somente DEPARTMENT/TENANT. Adoption usa 30 dias; Training precisa de elegibilidade
e regra de conclusão; não usar score de prova diretamente como score de dimensão.
Fórmula completa de Governance/subdimensões ainda precisa ser formalizada.
ADR-22 define SecurityComplianceRate=clamp(100*(1-eventosVioladores/totalRequisicoes),0,100).
Mapeamento de evento violador/atividade suficiente ainda pendente.

ADR-23, r=C/B no mesmo mês/moeda:
- BudgetHealth=100 se 0<=r<=0.8;
- =100-250*(r-0.8) se 0.8<r<=1;
- =50-250*(r-1) se 1<r<1.2;
- =0 se r>=1.2.
Sem orçamento válido: 0+INSUFFICIENT_DATA. Dimensão sem evidência: score 0
com status no breakdown, sem redistribuir pesos (ADR-21).

ADR-27: ScorePeriod=YearMonth; Governance/Utilization mensais, Adoption nos 30 dias
até fim do mês, Training as-of fim do mês. Snapshot imutável/versionado após fechar.
Global agrega fatos/denominadores e então calcula; não faz média simples de scores.

Utilização de licença proposta: licença ACTIVE, não expirada e atribuída no corte
é aplicável; conta como utilizada se houver uso na ferramenta pelo titular na
janela aprovada. Taxa=100*licençasAplicáveisUtilizadas/licençasAplicáveis. Mapear
license_status e datas do histórico e definir a janela. Nenhuma licença aplicável
deve ser distinguida de ausência de fonte; somente a primeira pode ser NOT_APPLICABLE.

## 8. Treinamentos, Shadow AI e cobertura

### Treinamentos e gargalos

TaxaConclusao=100*pessoasElegiveisConcluintes/pessoasElegiveisObrigadas.
Histórico traz inscrições/conclusões, não população obrigada; mapear status,
curso, data de corte e deduplicação antes do cálculo.
ROI_Treinamento%=100*(BeneficioIncrementalAtribuivel-CustoTreinamento)/CustoTreinamento.
Custo inclui curso+horasParticipacao*custoHora+outros custos atribuíveis registrados.
Incremento exige antes/depois, população comparável, controle da demanda e método
de atribuição validado. Sem isso, mostrar evolução pós-treinamento, não causalidade.
Gargalos: baixa conclusão/inatividade apenas com metas e janelas aprovadas. Não
inventar alerta de baixa produtividade com base em salário ou baixo uso.

**Implementado na Sprint 4:** o cruzamento treinamento × uso em quatro quadrantes
(treinado/não treinado × ativo/inativo na mesma janela de 30 dias), com contagens e
lista nominal apenas sob `USER:READ`. Taxa de conclusão formal, ROI e custo de
treinamento continuam pendentes de metas/fonte de custo.

### Uso homologado e limites de Shadow AI

AdocaoHomologada%=100*elegíveis com uso homologado/totalElegíveis, na janela definida.
Homologação considera approval_status, approval_date e allowed_departments no
instante do uso. Sem histórico de validade suficiente, classificação é desconhecida.
UsoNaoHomologadoIdentificado=SUM(request_count de registros classificados não homologados).
ParticipacaoNaoHomologada%=100*N_naoHomologado/N_classificado.
Classificar homologado/não homologado/desconhecido. Ausência de política não
significa automaticamente infração. Eventos de segurança não são requisições.

CoberturaClassificacao%=100*N_classificado/N_observado.
CoberturaMonitoramento%=100*unidadesMonitoradas/unidadesPrevistasNoInventario.
Unidade (dispositivos/ferramentas/usuários) deve ser explícita; sem inventário/fonte
externa, N/A. Não presumir que tráfego do Gateway observa IA externa da empresa.
VariacaoParticipacaoNaoHomologada_pp=taxaAtual-taxaAnterior.
ReducaoRelativa%=100*(taxaAnterior-taxaAtual)/taxaAnterior, se base>0.
Comparar só cobertura/fontes equivalentes. Chamar de redução do uso não homologado
observado, não da Shadow AI total. Não instalar monitoramento externo nesta etapa.

### Qualidade da medição/capacidade

CoberturaCusto%=100*requisiçõesComPreçoMoedaVálidos/N.
CoberturaLatencia%=100*requisiçõesComLatênciaVálida/N.
UtilizacaoCota%=100*requisiçõesNoPeriodo/cotaDoMesmoPeriodo.
Zero latência requer confirmação da instrumentação, não assumir que sentinela é
medição real. Falhas exigem universo completo de tentativas.

## 9. Tabelas/campos novos a criar futuramente (propostas)

Nada abaixo foi criado nesta entrega. Cada tabela pressupõe id UUID, tenant_id
obrigatório, created_at, índices começando por tenant_id e validações de valores.

> **Sprint 4:** a V023 criou `engagement_ai_licenses`, `engagement_training_records` e
> `engagement_credit_snapshots`. As demais tabelas/propostas abaixo continuam pendentes;
> `department_budget_periods`, preços versionados e custos de treinamento não foram criados.
Vigência/versão/moeda onde indicado. Referências entre módulos somente IDs,
sem FK/JPA cross-module. API pública do proprietário, nunca repository alheio.
Tipos/constraints finais e migrations dependem da task específica; não criar
toda esta lista de uma vez sem necessidade do incremento.

| Proprietário / tabela candidata | Campos específicos propostos | Finalidade |
|---|---|---|
| observability / observability_productivity_assumptions | calculation_version, valid_from/to, minutes_saved_per_interaction, success_weight, speed_weight, reference_window_start/end, source_kind, evidence_reference | Premissas reproduzíveis, não 3 minutos fixados silenciosamente |
| observability / observability_user_hourly_costs (restrita) | user_id, hourly_cost, currency, valid_from/to, source_kind, evidence_reference | Custo-hora autorizado; não adicionar salário ao app_user nem enviar custo-hora ao frontend |
| observability / observability_task_outcomes (para medição real) | task_id, user_id, department_id, completed_at, baseline_minutes, assisted_minutes, validation_status, measurement_method | TempoMedido=baseline-assisted, inclusive negativo; sem conteúdo, sem duplicar a tarefa |
| observability / observability_productivity_snapshots | scope_kind/id, period_start/end, nature, recorded_cost, covered_cost, estimated_hours, estimated_value, net_benefit, currency, coverage, data_status, calculation_version, source_cutoff | Agregados reproduzíveis; proteger inferência de custo-hora/salário |
| department / department_budget_periods | department_id, period_month, amount, currency, valid_from/to, version, changed_by, change_reason | Histórico mensal de orçamento e ajustes auditáveis |
| department / department_user_budget_allocations (opcional) | user_id, department_id, budget_period_id, amount, allocation_method, approved_by | Somente após aprovar política individual; sem relação com saldo de Credits |
| aigateway / aigateway_model_price_versions | model_id, effective_from/to, input_price_per_1k, output_price_per_1k, currency, billing_type | Vigência sem sobreposição por modelo |
| aigateway / aigateway_model_capacities | model_id, valid_from/to, capacity_unit, capacity_amount, contract_reference | Limites contratados, unidade explícita |
| aigateway / aigateway_request_audit | request_id, user_id, department_id, model_id, started_at, finished_at, status, error_category, latency_ms, estimated_cost, currency, cost_status | Todas as tentativas, sem segredos; origem/contrato de falhas a definir, não criar UsageRecord falso |
| observability / observability_fx_rates | base_currency, quote_currency, rate, rate_date, source_reference | Conversão rastreável com taxa positiva |
| engagement / engagement_training_records | user_id, course_id, enrolled_at, completed_at, status, score, duration_hours | Fatos operacionais equivalentes ao histórico |
| engagement / engagement_training_assignments | user_id, course_id, assigned_at, due_at, required | Denominador de capacitação e prazos |
| engagement / engagement_training_costs | course_id, period, direct_cost, participation_hours, currency, attribution_method | ROI treinamento; custo-hora via port autorizado |
| engagement / engagement_ai_licenses | user_id, ai_tool_id, assigned_at, expires_at, status | Máximo uma ACTIVE por tenant/user/tool; consolidar contrato AITool |
| engagement / engagement_credit_snapshots | user_id, period, usage_component, training_component, license_component, license_applicability, score, calculation_version, data_status | Credits auditável; metas/regras pendentes |
| governancescore / governancescore_snapshots | scope_kind/id, score_month, final_score, governance, adoption, training, utilization, component_statuses, calculation_version, closed_at | ADR-27; unicidade e correção versionada a definir |
| agentpolicies / agentpolicies_execution_metrics | execution_id, agent_id, user_id, department_id, started_at, finished_at, status, latency_ms | Identidade de execução; relação por IDs a request_id para somar custo uma só vez; tempos de etapas somente se instrumentados |
| observability / observability_security_events | source_event_id, occurred_at, user_id, department_id, ai_tool_id, event_type, severity, policy_id, action_taken, source_kind | Fatos operacionais deduplicados por fonte/evento |
| observability / observability_monitoring_coverage | period_start/end, source_kind, unit_type, monitored_count, expected_count, evidence_reference | Denominador verificável de cobertura |
| aigateway / aigateway_tool_approvals (se necessário) | ai_tool_id, department_id opcional, valid_from/to, approval_status | Reutilizar catálogo/políticas quando suficientes; formalizar AITool e vigência |
| platform / platform_resource_samples (ownership pendente) | sampled_at, resource_id, cpu_used_time, cpu_capacity_time, memory_used_bytes, memory_limit_bytes, sample_interval | Escopo de plataforma/infraestrutura exige ADR; não expor recursos compartilhados a um tenant |

Campos candidatos adicionais em **observability_usage_records**: currency,
cost_status, referência/snapshot da versão de preço, occurred_at e ai_tool_id
quando os contratos os fornecerem. Não reescrever registros imutáveis antigos
com preço atual. Valores legados incompletos continuam identificados. O snapshot
de custo deve chegar por evento/port; Observability não importa internals do Gateway.

Otimização exige futuramente scenario/inputs/allocations: scenario_id, período,
versão, preços/premissas, status do solver, demanda, limites e alocações user/model.
Ownership e contratos PENDENTES de ADR, antes de criar tabelas/módulo/dependência.
Não alterar allowlist para facilitar joins ou execução do solver nesta etapa.

Para análises as-of faltam histórico de elegibilidade/status e vínculo organizacional.
Equipe é departamento hoje; não inventar tabela team sem requisito. Integração de
fontes exige mapear employee_id/user_id, departamento textual/UUID e company/tenant.
Sem isso, exibir fontes separadas. Dados de hora/salário são restritos mesmo que
o benefício agregado seja publicável; grupos pequenos podem permitir inferência.

## 10. Mapeamento para as telas

| Painel | Fórmulas/seções |
|---|---|
| Colaborador | Consumo/custo/média (4); Credits/ranking (7); tempo, valor e benefício estimados (5); histórico próprio sem cálculo |
| Líder | Gastos/participação/comparativo/modelos/membros (4); ranking (7); capacitação/gargalos (8); valor/tempo/benefício/cobertura da equipe (5) |
| Gestor | Orçamento e agentes (4); adoção/score (7); ROI treinamento (8); benefício/retorno/cobertura (5); cenário/ganho/limites (6) |
| Executivo | Consolidados/benchmark (4); score/adoção (7); benefício/retorno/cobertura (5); oportunidades (6); homologação e monitoramento (8) |
| Governança/TI | Auditoria/status/latência/erros/infraestrutura/segurança (4); cobertura e capacidade (8), sem salário ou ranking econômico |

Exportações PDF/apresentação reproduzem resultados autorizados com natureza,
período, corte, cobertura, moeda e versão. Não são novos cálculos. Não exportar
salários, conteúdo privado ou dados de outro tenant.

## 11. Implementação futura e aceitação

- Identificar task Jira específica; SPI-92 cobre apresentação, não aprova solver.
- Definir parâmetros pendentes e ADRs sem substituir AI Credits/Governance Score.
- Implementar domínio/application Java com ports na allowlist; histórico em adapters.
- Criar somente migrations necessárias ao incremento escolhido.
- Testar isolamento tenant e escopo, moeda/vigência, preço desconhecido vs gratuito,
  cobertura parcial, divisores zero, empates, privacidade, score sem evidência,
  snapshot fechado, cenário inviável e ausência de dados de Shadow AI/treinamento.
- Espaços visuais não comprovam produtividade nem cobertura de toda Shadow AI.
