# ADR-43 — Orçamento mensal e saldo financeiro individual

## Status

ACCEPTED — LOCAL-BUDGET-001, regras autorizadas por Vulpi.

## Decisão

Department mantém orçamento empresarial mensal em BRL, percentuais por
departamento, limites individuais, reservas e ajustes auditáveis. O executivo
distribui valores. O gestor configura um percentual único de alerta empresarial.
Essa permissão específica tem alcance empresarial, sem ampliar os demais recursos.

O Gateway reserva antes de toda chamada e confirma o valor conhecido usando
preços fotografados e câmbio explicitamente configurado por modelo. Custos
desconhecidos mantêm a reserva pendente até conciliação pelo executivo.
Chamadas sem orçamento individual ou sem preço/câmbio configurado são negadas.
Orçamentos antigos permanecem históricos e não são convertidos implicitamente.

O orçamento é do mês-calendário no fuso configurado da empresa. A renovação
copia percentuais e pesos anteriores. Os limites são calculados automaticamente
com os funcionários ativos de cada departamento, inclusive novas admissões.
Gastos e reservas permanecem no mês de origem. Saldos não são transferidos.

O teto de saída vai ao provider. A reserva de entrada usa um limite conservador
de bytes UTF-8 e overhead de mensagens/ferramentas. O custo reportado é sempre
registrado, inclusive se um provedor exceder a reserva. Esse excesso bloqueia
novos gastos; a garantia depende de uso e limites respeitados pelo provedor.

## Ajuste LOCAL-BUDGET-001 — regras por departamento e hierarquia

O executivo configura orçamento geral, percentuais por departamento e pesos
por hierarquia. O limite de uma pessoa é calculado por
`parcela do departamento × peso da hierarquia / soma dos pesos dos funcionários ativos do departamento`.
Pessoas na mesma hierarquia e departamento recebem o mesmo limite. Os valores
individuais enviados pelo cliente são ignorados; planos novos persistem somente
as regras. O cálculo é refeito com o quadro ativo em cada consulta e reserva,
incluindo admissões e mudanças de departamento ou hierarquia.

Gastos continuam individuais. A reserva verifica também os tetos agregados do
departamento e da empresa. Isso protege o orçamento quando a composição da
equipe muda após haver gasto. A V026 registra o departamento na reserva e
recupera esse vínculo das atribuições legadas quando disponíveis.

Os controles ficam em modais nos dashboards autorizados: orçamento no executivo
e percentual empresarial de alerta no gestor. A consulta de limites é agrupada
por departamento e hierarquia, sem edição de valores por pessoa.

## Alternativas e consequências

Controle em observability exigiria nova dependência do Gateway e dependeria
da entrega assíncrona de métricas. O livro financeiro síncrono em department
permite serializar reservas no PostgreSQL sem alterar a allowlist modular.
Observability continua dono dos fatos analíticos. A regra anterior de orçamento
somente informativo (SPEC-02) passa a coexistir com limites individuais
bloqueantes. A barra do chat mostra gasto confirmado/limite, saldo e reservas.

Não há chamada cambial externa automática. Cada modelo exige moeda, taxa para
BRL e referência da taxa, informadas pelo executivo e fotografadas na reserva.
