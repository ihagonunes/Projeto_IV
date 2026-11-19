# LOCAL-BUDGET-001 — Orçamento mensal e limite financeiro individual de IA

Identificação local autorizada por Vulpi em 18/11/2026. Issue no Jira ainda
não criada. Implementada e validada localmente em 18/11/2026. Alterações sem commit.

## Objetivo

Ajuste solicitado: orçamento e alerta acessíveis por modal nos dashboards do
executivo e gestor. Os limites seguem regras por hierarquia e departamento,
com cálculo automático conforme o quadro ativo. A edição individual e a
atribuição manual para novos funcionários foram substituídas por esse cálculo.

Controlar o gasto de IA de cada funcionário a partir de um orçamento mensal
geral da empresa, distribuído por percentuais departamentais e limites
individuais. Exibir consumo financeiro, saldo e alertas no chat.

## Regras alinhadas com o usuário

- O executivo define o orçamento empresarial, percentuais departamentais,
  pesos de distribuição por hierarquia, inclusive ajustes da política.
- O executivo confirma as regras por hierarquia e departamento; o servidor calcula os limites automaticamente.
- A soma dos percentuais departamentais não ultrapassa 100%. A soma dos limites
  individuais não ultrapassa o valor atribuído ao departamento.
- O gestor configura um percentual de alerta único para toda a empresa,
  aplicado a todos os cargos sobre o limite individual de cada funcionário.
- O administrador do ATLAS opera a plataforma e não participa dessas decisões empresariais.
- O período é o mês-calendário no fuso da empresa. Sobra individual vale até
  o fim do mês, sem transferência automática de saldo para o próximo mês.
- Na virada do mês, copiar orçamento, percentuais e critérios do mês anterior,
  permitindo ajustes pelo executivo. O consumo do novo período começa zerado.
- Funcionários que entram durante o mês recebem automaticamente o limite
  calculado para sua hierarquia e departamento.
- A moeda do orçamento é BRL. Preços de modelos em outra moeda exigem registrar
  a taxa de conversão aplicada ao gasto.
- Chat e agentes consomem o limite do funcionário que iniciou as chamadas.
- O backend reserva valor antes da execução, desconta o gasto confirmado e
  libera a diferença. Reservas pendentes reduzem o saldo disponível.
- O alerta considera o gasto confirmado. A barra do chat mostra valor gasto
  em relação ao limite individual, com reservas e saldo apresentados separadamente.
- Solicitações que não couberem no saldo disponível devem ser bloqueadas antes
  de chamar o provedor.

## Critérios de aceitação

- Executivo consegue distribuir e redistribuir os valores com validação de teto.
- Gestor consegue configurar o percentual empresarial sem obter outras
  permissões empresariais além das necessárias para essa operação.
- Funcionário consulta somente seu limite, gasto, reservas, saldo e vigência.
- O chat mostra o alerta ao atingir o percentual configurado, com consumo
  financeiro substituindo a escala visual de tokens.
- Chamadas concorrentes não reservam o mesmo saldo. Repetições, conclusão e
  reentrega não duplicam descontos ou liberações.
- Reduções dos tetos empresarial e departamental respeitam gastos e reservas já existentes.
- Testes cobrem permissões, isolamento entre tenants, vigência, virada do mês,
  ingresso durante o mês, distribuição, concorrência e tratamento de falhas.
- Histórico, eventos e auditoria financeira registram metadados necessários,
  preservando as regras de privacidade de prompt, resposta e documentos.
- Migrations e estratégia de transição tratam orçamentos e gastos existentes.

## Módulo proprietário e arquitetura

Conforme ADR-43, `department` mantém
orçamento, distribuição e controle financeiro; `aigateway` integra a reserva
na execução central de IA via API pública; `observability` mantém as leituras
analíticas; `iam` aplica permissões; a interface do chat exibe o saldo pessoal.

A mudança de orçamento apenas informativo para bloqueio de gasto exige
atualizar a decisão atual da SPEC-02 e o ADR relacionado explicitamente.
Definir contratos, migrations e acessos cross-module conforme a allowlist.

## Decisões técnicas da entrega

- Fuso definido na configuração financeira da empresa, inicialmente
  `America/Sao_Paulo`, editável antes de criar o orçamento vigente.
- Limites em centavos, livro financeiro com 8 casas decimais e resíduos da
  sugestão disponíveis ao executivo no departamento.
- Moeda e taxa por modelo, com referência manual e fotografia na reserva.
- Reserva conservadora por bytes e overhead da entrada; limite de saída de
  2048 tokens enviado ao provider.
- Cobranças desconhecidas permanecem reservadas. Após 10 minutos, o executivo
  pode conciliar mediante referência da cobrança. Callback tardio com gasto
  superior gera ajuste, evitando omitir cobrança após uma conciliação.
- Os limites acompanham o quadro ativo e a hierarquia de cada pessoa. Contas
  novas ou transferidas recebem o limite calculado automaticamente. Compromissos
  de contas desativadas permanecem no orçamento do mês de origem.
- O executivo confirma o primeiro orçamento e as moedas. Gastos anteriores à
  ativação continuam históricos e não são convertidos implicitamente para BRL.
- A implementação e execução do pipeline visual de agentes é uma tarefa própria.
  Esta tarefa deve garantir o controle financeiro nas chamadas que usam o Gateway.

## Contexto

- `ai/architecture.md`
- `ai/contracts.md`
- `ai/testing.md`
- `ai/decisions.md`
- `ai/adr/ADR-06-budget-ownership.md`
- `ai/tasks/sprint-03/SPEC-02-orcamento.md`

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/department/`
- `src/main/java/br/com/qsoft/governance/aigateway/`
- `src/main/java/br/com/qsoft/governance/iam/`
- `src/main/java/br/com/qsoft/governance/observability/`
- `src/main/resources/db/migration/`
- `src/main/resources/static/public/js/pages/chat.js`
- Testes dos módulos e interface afetados.

## Checks

Ajuste dos modais e cálculo automático: 12 testes de regras de orçamento,
4 testes de frontend e 6 cenários de integração financeira passaram. Nesta
etapa foram executados apenas os testes relacionados às alterações, conforme
solicitação do usuário. Nenhum commit foi realizado.


Validação local concluída: 1147 testes unitários/arquitetura e 102 testes de
frontend passaram. A suíte de integração com PostgreSQL foi executada; os
cenários afetados de chat, provider HTTP, agente, instalação e livro financeiro
foram repetidos após ajustes, incluindo o bloqueio de modelos legados sem preço.
O teste de concorrência comprovou que duas reservas não gastam o mesmo saldo.
A conferência visual no navegador interno ficou bloqueada por falha de
inicialização da ferramenta. Renderização EJS e endpoints foram verificados.

- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
- Testes relevantes de frontend.
