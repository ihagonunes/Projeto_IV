# Sprint 2 — Tasks Reconciliadas

- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Período:** 08/09/2026 a 18/09/2026
- **Total:** 64

## Grupos
- `POPROJ-51` — Multi-tenancy & Isolamento Lógico (2 tasks)
- `POPROJ-15` — Autenticação, Sessões e Segurança (6 tasks)
- `POPROJ-14` — Gestão de Usuários e Convites (8 tasks)
- `POPROJ-16` — Atribuição de Papéis (1 tasks)
- `POPROJ-52` — RBAC Completo & Matriz de Permissões (13 tasks)
- `POPROJ-13` — Cadastro de Tenant, Identidade Visual & Onboarding (7 tasks)
- `POPROJ-23` — Pipeline de Métricas, Logs de Uso e Custos de IA (10 tasks)
- `POPROJ-21` — Roteamento Determinístico e Modelos de IA (8 tasks)
- `POPROJ-53` — Dashboard de Métricas, Filtros e Agregações (9 tasks)

## Tasks com reconciliação explícita
- **SPI-26** — Middleware de verificação de token nas rotas protegidas
- **SPI-39** — Definir modelo de dados de permissões (resource, action, role, department)
- **SPI-53** — Configuração de identidade visual (white-label)
- **SPI-56** — Auditoria de criação/alteração do tenant
- **SPI-57** — Status do tenant (ativo/suspenso)
- **SPI-63** — Edição de papel/departamento de usuário existente
- **SPI-66** — Expiração e renovação de sessão/token
- **SPI-67** — Rate limiting / bloqueio após tentativas inválidas
- **SPI-69** — Modelagem do schema ai_usage_log
- **SPI-70** — Middleware de captura de métricas no Gateway
- **SPI-71** — Endpoint de persistência do log de uso
- **SPI-72** — Cálculo de custo estimado por requisição
- **SPI-74** — Tratamento de falhas na captura de métricas
- **SPI-75** — Carga do dataset sintético da QSOFT como massa de teste
- **SPI-78** — Testes de integração do pipeline de logging
- **SPI-79** — Modelagem do schema de Model
- **SPI-80** — Modelagem do schema de permissão modelo × departamento
- **SPI-81** — Endpoint de cadastro/ativação/desativação de modelo
- **SPI-82** — Endpoint de configuração de departamentos por modelo
- **SPI-83** — Lógica de roteamento determinístico no Gateway
- **SPI-84** — Endpoint de consulta do modelo permitido para o usuário autenticado
- **SPI-85** — Tratamento de erro quando departamento não tem modelo configurado
- **SPI-86** — Testes do roteamento determinístico
- **SPI-94** — Aplicação do RBAC no dashboard

## Precedência para agente
1. Seção `Versão reconciliada com a arquitetura` da task.
2. ADRs aceitos.
3. `ai/architecture.md` / arquitetura consolidada.
4. Fonte original do Jira/consolidado para intenção funcional.

A reconciliação não altera o Jira.
