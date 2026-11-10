# Dashboard, acesso e administração

Estado consolidado em 23/10/2026. Abrange POPROJ-53/54 e `FEAT-DASH-001`
(identificação local autorizada; não é uma issue Jira remota).
[Banco e importação](BANCO_E_IMPORTACAO_HISTORICA.md) ·
[Validação e pendências](VALIDACAO_E_PENDENCIAS.md).

## Perfis e navegação

O login usa `app_user` e concessões IAM. `hierarchy_level` é rótulo organizacional;
não concede permissões. Administradores têm o papel `TENANT_ADMIN`, sem tabela
ou autenticação paralela. Tenant vem da sessão validada, nunca do navegador.

| Perfil | Botões no onboarding | Visão analítica |
|---|---|---|
| Colaborador | Dashboard, Voltar | Próprio usuário |
| Líder | Dashboard, Voltar | Departamentos concedidos e próprio usuário |
| Gestor | Dashboard, Convites, Voltar | Departamentos concedidos e próprio usuário |
| Executivo | Dashboard, Convites, Voltar | Empresa autenticada |
| Administrador | Novo Cadastro, Convites, Identidade visual, Voltar | Sem dashboard |

`/dashboard` escolhe a apresentação pelas concessões atuais: TENANT_ADMIN tem
prioridade administrativa; os demais seguem EXECUTIVE, MANAGER, LEADER e
COLLABORATOR. Administrador que abre qualquer `/dashboard/:profile` volta ao
onboarding. O perfil na URL não autoriza dados. O protótipo `/dashboards` foi removido.
Cadastro (`/tenant`), edição cadastral e identidade visual são páginas administrativas.

V021 retira `USAGE:READ` e `GOVERNANCE_SCORE:READ` de `TENANT_ADMIN`; contas
exclusivamente administrativas recebem 403 nas consultas analíticas. Permanecem
18 permissões administrativas. Outros papéis e a união de concessões continuam
conforme ADR-36. A API mantém o contrato público de bootstrap de novo tenant;
o controle das páginas não redefine esse contrato.

## Sessão no navegador

O BFF estabelece JWT em cookie `qsoft_access`: HttpOnly, SameSite=Strict,
Path=/, duração de 30 minutos e Secure em produção. Não retorna JWT no JSON
nem o persiste em storage. A flag `qsoft.session` serve somente à navegação.
Spring valida assinatura, expiração, usuário ativo e tenant; o BFF consulta
`/usuarios/me/permissoes` antes de renderizar as páginas protegidas.

Login: POST `/api/v1/auth/login`; sessão: GET `/api/v1/auth/session`; logout:
POST `/api/v1/auth/logout`, expirando o cookie. Escritas por cookie exigem
`X-Qsoft-Request: 1` e origem permitida. A API Spring direta mantém Bearer.
Ver emenda ADR-30. O botão Voltar de Convites aponta para `/onboarding`.

## Dashboard e filtros

Filtros ficam somente na sidebar. Aplicam-se período, modelo e, quando autorizado,
departamento e usuário. Respostas atrasadas são descartadas; erros/recortes vazios
limpam resultados anteriores. Autocomplete de usuário exige `USER:READ`,
`USAGE:READ` e papel MANAGER ou EXECUTIVE; busca e seleção são validadas no backend.

| Fonte | Contrato |
|---|---|
| Operacional | GET `/api/v1/observability/metrics/usage` e `/costs`, com `USAGE:READ` e escopo IAM |
| Pessoal operacional | `/metrics/me/usage` e `/me/costs`, fixados no usuário autenticado |
| Histórico autorizado | GET `/api/v1/observability/metrics/historical`, com vínculos explícitos e escopo IAM |
| Histórico pessoal | `/metrics/historical/me`, fixado no vínculo do usuário autenticado |
| Modelos operacionais | GET `/api/v1/ai-gateway/models/dashboard`, retorna `{id,name}` dos modelos AVAILABLE do tenant |
| Busca de usuários | GET `/api/v1/usuarios?name=...&size=20`, com autorização e escopo |

No operacional, datas `YYYY-MM-DD` representam dias UTC completos; timestamps
ISO continuam aceitos. Sem datas, padrão de 30 dias até agora. Intervalo invertido
retorna 400. A sidebar operacional inicia hoje menos 30 dias até hoje.

O colaborador exibe consumo histórico pessoal e, desde 30/10/2026, também o uso
operacional pessoal em `Meu uso operacional`, sem orçamento ou KPIs de terceiros. Primeiro carregamento usa o último dia da cobertura pessoal e 30 dias
anteriores, preenchendo a sidebar. Sem vínculo autorizado retorna 403, sem ampliar
para o tenant. `userId`, `employeeId` ou `tenantId` enviados pelo cliente não
mudam o titular do endpoint pessoal.

## Histórico: escopo e cálculo

Cadeia de identidade: usuário autenticado → `iam_legacy_user_links` →
`legacy_employee_id` → logs. Departamentos usam `department_legacy_links`.
Observability consulta as portas públicas de IAM/department; não há FK cross-module.
SELF filtra funcionário; DEPARTMENT filtra departamentos concedidos mais o próprio
histórico; TENANT filtra empresa. Agregados, cobertura e catálogos respeitam o recorte.
`userId` opcional no endpoint geral é UUID atual autorizado antes de resolver o vínculo.

IDs históricos são textuais; opções da sidebar usam o prefixo `historical:`.
Não equivaler modelos/departamentos por nome ou converter UUIDs automaticamente.
Recorte sem correspondência limpa o painel incompatível e informa o motivo.
Histórico e operação permanecem separados, conforme ADR-26.

Datas históricas são civis, sem fuso inventado; `from`/`to` são inclusivos, com
limite exclusivo do dia seguinte. Leitura em snapshot REPEATABLE_READ. Resposta:
`sourceKind=HISTORICAL`, `currency=USD`, cobertura, período, ativos, catálogos,
série diária e custos por modelo.

| Indicador | Regra |
|---|---|
| Requisições | SUM(request_count) |
| Funcionários com uso | COUNT DISTINCT employee_id quando request_count > 0 |
| Tokens | Soma dos totais das linhas, sem multiplicar pelas requisições |
| Custo estimado USD | (tokensEntrada × preçoEntrada + tokensSaída × preçoSaída) / 1000, com numeric |
| Cobertura do custo | Preço/tokens ausentes ou preço negativo geram unpricedRecords; custo parcial ou indisponível |

O catálogo não tem vigência: estimativa não equivale a faturamento ou preço efetivo
na data. Não somar moedas ou fontes distintas. Não consultar conversas ou salários.

## Orçamentos e alertas

Tela `/onboarding/departamentos/orcamentos`; consulta GET
`/api/v1/observability/orcamentos?mes=AAAA-MM`, padrão mês UTC corrente.
Leituras analíticas respeitam `USAGE:READ` e escopo. Edição requer
`DEPARTMENT_BUDGET:MANAGE`, concedida ao executivo no próprio tenant: PUT
`/api/v1/departamentos/{id}/orcamentos/{mes}`, com `amount` e `alertThresholdPct`.

- Matriz mostra orçamento, consumo, percentual, situação e histórico de 12 meses.
- Modal permite zero; rejeita vazio, negativo, exponencial, precisão superior a
  duas casas ou mais de 13 dígitos inteiros. Limiar inteiro de 1 a 100.
- Barra verde abaixo de 80%, amarela de 80% a menos de 100%, vermelha a partir
  de 100%. Largura máxima de 100%, mantendo o percentual real no texto.
- Alertas no topo do dashboard usam o mês corrente, independentes dos filtros.
  Badge/lista expansível mostram áreas autorizadas; 401/403 ocultam o painel.
- Ausência de orçamento difere de zero; zero com consumo não recebe percentual
  fictício. Valores, situação e autorização vêm do backend.

## Projeção do mês

GET `/api/v1/observability/orcamentos/projecao?departmentId=&mes=AAAA-MM&janelaDias=`
(ADR-41, `projection-v1`). Exige `USAGE:READ` com checagem
`isAllowed(DepartmentTarget)`; `departmentId` é obrigatório, `mes` é o corrente UTC por
padrão e `janelaDias` aceita 7 a 60 (padrão 14). Mês futuro/inválido ou janela fora do
intervalo retorna 400; departamento de outro tenant retorna 404.

- `gastoAteHoje` soma o custo conhecido do departamento no mês, incluindo hoje.
- A média diária usa a janela `[hoje − janelaDias + 1, hoje]`, inclusive atravessando
  o mês anterior; dias sem uso contam como zero.
- `projetado = gastoAteHoje + média diária × dias restantes`; o dia de esgotamento é o
  primeiro dia em que o gasto projetado alcança o orçamento, ou nulo se não ocorrer.
- Mês passado devolve o realizado do mês inteiro, com `diasRestantes = 0`.
- Sem orçamento a recomendação é `SEM_ORCAMENTO`, sem exceção e sem percentual.
- O card do dashboard mostra gasto, projetado, dia de esgotamento e recomendação em
  texto; cor não é a única pista. O cálculo não chama LLM.

## Identidade visual

Página `/tenant/identidade-visual`, com retorno ao onboarding. GET/PUT
`/api/v1/tenants/me/identidade-visual`; escrita exige `TENANT:MANAGE`.
Salva URL da logo de até 500 caracteres, preserva cores existentes e permite
remover a logo. Usa dados reais e só confirma sucesso após a API salvar.
Não oferece upload simulado. O nome da empresa é somente leitura nessa tela.

## Localização da implementação

| Grupo | Arquivos principais |
|---|---|
| Sessão e navegação | `static/modules/auth/browser-session.js`, `static/app.js`, rotas auth/dashboard, `dashboards.controller.js`, onboarding EJS |
| Métricas e histórico | `observability/application/QueryHistoricalDashboard.java`, `HistoricalDashboardQuery`, `HistoricalDashboardAdapter`, controllers de métricas e datas |
| Vínculos | `HistoricalIdentityQuery/Adapter`, `HistoricalDepartmentQuery/Adapter`, V020 |
| UI analítica | `dashboard/views/index.ejs`, `dashboard-metrics.js`, `dashboard-historical.js`, `dashboard-budget-alerts.js`, `orcamentos.js` |
| Administração | `SystemRole.java`, V021, views de Convites/Identidade visual, `identidade-visual.js` |

Java está em `src/main/java/br/com/qsoft/governance/`; UI em
`src/main/resources/static/`. ADRs e contratos canônicos continuam em `ai/`.
