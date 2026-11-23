# Arquitetura

## Estilo

Monólito Modular Java/Spring Boot, um único projeto Maven e um único PostgreSQL no MVP.

```text
br.com.qsoft.governance
├── sharedkernel
├── iam
├── department
├── project
├── aigateway
├── chat
├── agentpolicies
├── agents
├── observability
├── engagement
├── governancescore
└── platform
```

## Estrutura interna padrão

```text
<module>/
├── api/
├── domain/
├── application/
└── infrastructure/
```

Exceção: `sharedkernel` não segue layers de negócio; contém apenas tipos transversais em `id/`, `event/`, `tenant/`, `exception/` e `rbac/` (vocabulário `resource × action` — ADR-34).

Direção interna:

```text
infrastructure → application → domain
```

Outro módulo pode acessar somente `<module>.api.*`.

## Ownership

| Módulo | Ownership principal |
|---|---|
| sharedkernel | IDs, DomainEvent, TenantContext, exceções base, vocabulário de autorização (`Resource`/`Action`/`ScopeType`/`AccessTarget`/`VisibilityScope`/`Targeting` — ADR-34/ADR-36) |
| iam | Tenant, User, autenticação, papéis de sistema, concessões com escopo (`RoleAssignment`), `AuthorizationQuery`, contrato `@RequiresPermission` (ADR-36/ADR-37) |
| department | Department, Area, UserAreaMembership, BudgetLimit, ModelPolicy, orçamento empresarial mensal e livro financeiro individual (ADR-43) |
| project | Project mínimo |
| aigateway | Provider, AITool, Model, credenciais por referência, completion |
| chat | Conversation, Message |
| agentpolicies | KnowledgeDocument/Version, AgentExecution, KnowledgeAccess |
| agents | AgentDefinition global + override por tenant, AgentFlow/Version (ADR-44; V027/V028). AgentFlowExecution/Step futuros (ADR-45) |
| observability | UsageRecord, custo, BudgetStatus, Alert |
| engagement | AILicense, TrainingRecord, snapshots mensais de AI Credits (V023), adoção e cruzamento treinamento × uso |
| governancescore | score, breakdown, snapshot/histórico |

O `engagement` é dono de `engagement_ai_licenses`, `engagement_training_records` e
`engagement_credit_snapshots` (V023). Ele consome elegíveis de `iam.api`, dias ativos
de `observability.api` e catálogo de `aigateway.api`; não lê tabelas desses módulos.
O histórico é lido pelo adapter nativo do próprio módulo com `company_id` explícito.

## Allowlist síncrona

| Consumidor | Pode depender de |
|---|---|
| iam | sharedkernel |
| department | sharedkernel, iam |
| project | sharedkernel, iam, department |
| aigateway | sharedkernel, iam, department |
| chat | sharedkernel, iam, aigateway |
| agentpolicies | sharedkernel, iam, department, aigateway |
| agents | sharedkernel, iam, aigateway, agentpolicies |
| observability | sharedkernel, iam, department |
| engagement | sharedkernel, iam, observability, aigateway |
| governancescore | sharedkernel, iam, engagement, observability |

ArchUnit deve falhar para qualquer dependência não declarada ou import interno cross-module.

> **Agentes e chat:** a seta `chat -> agents.api` não existe no MVP de agentes.
> Ela só pode ser adicionada com o ADR-45 e a allowlist explícita; a integração
> inicial usa o endpoint de execução de agentes, sem alterar o turno do chat.

## Multi-tenancy

- `tenant_id` obrigatório em dados empresariais relevantes.
- TenantId deriva do contexto autenticado.
- Hibernate filter pode ser defesa transversal, nunca única defesa.
- SQL nativo tenant-aware filtra `tenant_id` explicitamente.
- testes provam Tenant A ≠ Tenant B.

## Persistência

- PostgreSQL único.
- tabelas/repositories pertencem ao módulo.
- FK intramodule permitida.
- FK/JPA association cross-module proibida.
- Flyway é a única evolução de schema.
- Hibernate usa `ddl-auto=validate`.
- histórico QSOFT fica no schema `historical` (V014), sem FK operacional; não
  confundir com as tabelas legadas `public.historical_*` do dump antigo.
- vínculos para identidades históricas são de propriedade IAM
  (`iam_legacy_user_links`) e department (`department_legacy_links`, V020).
  Observability acessa somente suas portas públicas HistoricalIdentityQuery e
  HistoricalDepartmentQuery. RBAC é resolvido pelo IAM atual; logs permanecem
  históricos e toda consulta limita company_id ao tenant autenticado (ADR-26).

## AI Gateway

```text
requestedModelId?
├─ sim → precisa pertencer a allowedModelIds
└─ não → defaultModelId

Model AVAILABLE
→ Provider ACTIVE
→ Provider Adapter
→ CompletionResult
→ AIRequestCompleted
```

Múltiplos Providers podem estar ACTIVE. Provider local não implica Docker.

## Agentes (ADR-44/45)

Status: a fundação existe no código (`br.com.qsoft.governance.agents` com
`api/`, `domain/`, `application/` e `infrastructure/`), com persistência de
catálogo/overrides/fluxos/versões (V027), RBAC `AGENT:*` (V028), seed global de
17 definições (V029), API de catálogo/override (BE-02), CRUD de fluxos com
versão imutável, `If-Match`/412 e soft delete (BE-03), validação server-side
do DAG + publicação (BE-04) e execução síncrona determinística com etapas e
recibos (BE-06, V030; V031 remove `result_text` pela decisão 4-B). A integração
com o chat continua fora do MVP e exige emenda ao ADR-45 + allowlist.

> **Sem RAG nesta fase.** O nó de políticas **não** consome `agentpolicies`
> (RAG de políticas) no MVP: o agente trabalha apenas com o material da tarefa.
> `agentpolicies` continua um módulo separado; a porta pública só existirá se o
> RAG for retomado. O seed marca 6 agentes "Gerais" como `ACTIVE` e os demais
> como `DRAFT` (prévia), e `executable` não substitui `AGENT:RUN` nem a
> validação do BE-04/06.

O módulo `agents` é o dono do catálogo e dos fluxos determinísticos de agentes:
`AgentDefinition`, `AgentFlow`, `AgentFlowVersion`, `AgentFlowExecution` e
`AgentFlowStep`. Ele não é dono de `Conversation`/`Message` (chat), de
`KnowledgeDocument` (agentpolicies) nem de `Provider`/`Model` (aigateway).

- O catálogo global guarda `agent_key` estável, prompt/template versionado,
  allowlist de ferramentas, escopo de conhecimento e política de modelo; o tenant
  pode sobrescrever esses campos em `AgentDefinitionOverride`.
- O fluxo é um DAG validado no servidor: uma tarefa, um resultado, sem ciclos,
  sem arestas duplicadas, com limites de nós/arestas e versão de schema.
- A execução é determinística por versão publicada (ADR-45 aceito); cada etapa
  usa `AICompletionService` e cada tentativa/recibo continua no `aigateway`.
  O resultado final não é persistido (decisão 4-B).
- A execução não lê tabelas de outro módulo e não usa FK cross-module.
- O caminho inicial é um endpoint de execução próprio. Integração com o chat
  (`chat -> agents.api`) só entra depois de ADR-45 aceito e da allowlist
  atualizada; não há dependência de chat no MVP de agentes.
- `agentpolicies` permanece especializado em conhecimento/políticas; um nó de
  políticas pode consumir uma porta em `agentpolicies.api` quando essa superfície
  existir.
- Persistência: `agents_catalog` (global, sem `tenant_id`),
  `agents_catalog_overrides`, `agents_flows` e `agents_flow_versions`. Tabelas de
  execução em V030 (`agents_flow_executions`/`agents_flow_steps`), sem conteúdo.
- As execuções não guardam prompt, entrada, resposta de etapa nem resultado final
  (decisão 4-B, 23/11/2026). GET devolve status/etapas/recibos; o conteúdo só
  aparece na resposta síncrona do POST.

## Segurança

- login: `email + password`; `tenantSlug` é **opcional** e, quando ausente, o tenant é
  resolvido pelo domínio de e-mail cadastrado (ADR-32, emendado pela SPI-54);
- BCrypt;
- JWT Bearer stateless, TTL 30 min, sem refresh no MVP;
- claims estáveis: UserId/TenantId + iat/exp;
- cada requisição protegida lê as **concessões ativas** do IAM, sem cache; revogar vale
  na requisição seguinte (ADR-36/ADR-37);
- permissões/escopos vêm do IAM atual (nunca do frontend ou do LLM);
- Tenant/User ACTIVE revalidados;
- Chat é privado inclusive de MANAGER/ADMIN;
- LLM nunca decide autorização.

## Pendências de decisão (não decidido)

- **Escopo de plataforma (SPI-57).** A baseline assume que todo principal autenticado possui `tenant_id` e é tenant-scoped (ADR-03/ADR-30). O bloqueio global de tenant (`TenantStatus` `ACTIVE`/`INACTIVE`) já é aplicado no login (`AuthenticateUser`) e em requests protegidos (`TenantContextFilter`), porém **não há ator autorizado a alterar o status**: a task pressupõe um "admin de plataforma" que não existe, e suspender a partir do próprio tenant gera lockout irreversível. Exige ADR antes de virar endpoint — ver `ai/decisions.md` → *Decisões abertas*.

- **Domínio de e-mail corporativo (SPI-54) — resolvido.** Cada tenant passa a ter um domínio único (`iam_tenants.email_domain`, migration `V007`), validado no cadastro (409 em sobreposição). A resolução do tenant no login usa o domínio **cadastrado**, com fallback legado (ADR-32 emendado). Pendência de frontend: `emailDomain` é obrigatório em `POST /api/v1/tenants`.

## Ambientes

```text
DEV LOCAL
Spring Boot local → PostgreSQL local

REPRODUZÍVEL
Docker Compose → backend + PostgreSQL
```

Docker não cria container por módulo.
