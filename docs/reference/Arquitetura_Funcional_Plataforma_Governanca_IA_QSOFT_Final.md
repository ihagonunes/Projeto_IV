ARQUITETURA FUNCIONAL
DA PLATAFORMA DE GOVERNANÇA DE IA

Projeto Integrador IV — QSOFT

Documento consolidado de decisões arquiteturais, domínio, integrações e regras transversais

| Item | Definição |
| --- | --- |
| Estilo arquitetural | Modular Monolith |
| Backend | Java + Spring Boot |
| Persistência | PostgreSQL compartilhado com isolamento lógico por módulo e tenant |
| Objetivo | Governança corporativa de uso de IA, observabilidade/FinOps, engajamento e score executivo |
| Status | Arquitetura funcional, física, de segurança e ADRs formais consolidados |
| Fora desta versão | Microservices, SSO corporativo avançado, RAG/banco vetorial obrigatório e orquestração de produção além do escopo do MVP |

Agosto de 2026

## 1. Objetivo e escopo do documento

Este documento formaliza a baseline arquitetural funcional da plataforma de governança corporativa de IA desenvolvida no contexto do Projeto Integrador IV em parceria com a QSOFT. Ele consolida as decisões de domínio, limites de módulos, ownership de dados, contratos de integração, eventos, regras de autorização, multi-tenancy e principais invariantes necessárias para iniciar a implementação com uma referência única e consistente.

A arquitetura física do projeto Java/Spring foi refinada em etapa subsequente. A solução permanece um único projeto Maven e um Monólito Modular. PostgreSQL é o único SGBD suportado pela baseline. A execução pode ocorrer de duas formas equivalentes: Spring Boot + PostgreSQL executados localmente, ou Docker Compose com backend + PostgreSQL. Docker é uma estratégia de empacotamento/deployment e não cria um container por módulo. Esta versão também incorpora as decisões finais de API, segurança, histórico analítico, testes arquiteturais, estrutura de packages e ADRs formais.

## 2. Contexto do produto

Plataforma white-label de governança de IA para a QSOFT e empresas clientes.

Objetivo principal: reduzir Shadow AI, governar acesso a ferramentas/modelos, controlar custos e fornecer visibilidade executiva sobre adoção e governança.

Um único Agent de MVP: agente corporativo de políticas/conhecimento interno, com acesso somente a conhecimento autorizado.

RAG permanece uma implementação opcional atrás de uma abstração de acesso ao conhecimento; embeddings e banco vetorial não são requisitos obrigatórios do MVP.

LLMs nunca decidem autorização. Autorização é determinística e acontece antes do acesso a recursos ou conhecimento.

AI Credits e Governance Score são indicadores analíticos; não bloqueiam acesso à IA.

## 3. Princípios arquiteturais

Modular Monolith: um deploy e um PostgreSQL no MVP, com limites lógicos fortes entre módulos.

Cada dado transacional possui uma única fonte de verdade.

Integração entre módulos somente por Command Port, Query Port ou Domain Event.

Entidades, JPA repositories e services internos não atravessam fronteiras de módulos.

Relações cross-module são representadas por IDs, nunca por associações JPA entre Aggregates de módulos distintos.

Domínio não depende de Spring, JPA, SDK de provider ou infraestrutura.

Uma transação deve permanecer dentro de um único módulo sempre que possível.

Eventos representam fatos/consequências; não são usados como RPC assíncrono.

Multi-tenancy é obrigatório em todas as operações de negócio.

Conteúdo de prompts, respostas, documentos e credenciais não atravessa para módulos analíticos.

## 4. Mapa de módulos

shared-kernel
├── iam
├── department
├── project
├── ai-gateway
├── chat
├── agent-policies
├── observability
├── engagement
└── governance-score

| Módulo | Responsabilidade central |
| --- | --- |
| shared-kernel | IDs e tipos realmente transversais; não é módulo de negócio. |
| iam | Tenant, User, autenticação, hierarquia, permissões e autorização. |
| department | Department, Area, UserAreaMembership, BudgetLimit e ModelPolicy. |
| project | Cadastro e ciclo de vida mínimo de Project. |
| ai-gateway | Provider, AITool, Model, seleção, routing, credenciais e execução de IA. |
| chat | Conversation, Message e privacidade do conteúdo conversacional. |
| agent-policies | KnowledgeDocument, versões, Agent, AgentExecution e acesso ao conhecimento. |
| observability | UsageRecord, métricas de uso/custo, BudgetStatus e Alert. |
| engagement | AILicense, TrainingRecord, adoção, métricas e AI Credits. |
| governance-score | Normalização e cálculo do AI Adoption & Governance Score. |

## 5. Shared Kernel

O shared-kernel contém apenas tipos comuns sem regra de negócio específica. Ele pode ser dependência dos módulos de negócio, mas nunca depende deles.

TenantId

UserId

DepartmentId

AreaId

ProjectId

ModelId

DomainEvent

TenantContext

Exceções base

Não pertencem ao shared-kernel: User, Department, Model, repositories, services de negócio ou DTOs específicos de módulos.

## 6. IAM — identidade, autenticação e autorização

### 6.1 Aggregates e estados

Tenant
├── TenantId
├── Slug (globalmente único)
├── Name
├── Status: ACTIVE | INACTIVE
└── timestamps

User
├── UserId
├── TenantId
├── Email (único dentro do tenant)
├── PasswordHash
├── Status: ACTIVE | INACTIVE
├── HierarchyLevel
├── DepartmentId
└── Permissions

HierarchyLevel possui COLLABORATOR, LEADER, MANAGER e EXECUTIVE. ADMIN não é um nível hierárquico; é um conjunto de permissões administrativas.

### 6.2 Autenticação

O MVP usa tenantSlug + email + password para resolver o tenant antes da autenticação e JWT Bearer stateless para operações protegidas. A senha é armazenada exclusivamente como hash BCrypt. Tenant e User devem estar ACTIVE no login e são revalidados nas requisições protegidas; uma desativação deve produzir efeito mesmo que exista token ainda não expirado.

tenantSlug + email + password
→ resolve Tenant por Slug
→ validar Tenant ACTIVE
→ AuthenticateUser
→ BCrypt verify + User ACTIVE
→ emitir JWT

JWT válido criptograficamente
→ estabelecer TenantContext
→ revalidar Tenant/User
→ resolver permissões e AuthorizationScope atuais
→ continuar ou negar

O access token possui TTL de 30 minutos e não há refresh token no MVP. Claims estáveis: sub = UserId, tenant_id = TenantId, além de iat/exp. Permissões, HierarchyLevel e escopos organizacionais não são congelados no token; permanecem sob autoridade do estado atual do IAM.

### 6.3 Autorização

ALLOW = Tenant ACTIVE AND User ACTIVE AND sameTenant AND permission granted AND organizational scope satisfied

Outros módulos não devem codificar regras de hierarquia diretamente. Eles utilizam IdentityQuery e AuthorizationQuery.

IdentityQuery.getUserContext(UserId) → UserContext
AuthorizationQuery.checkPermission(...) → AuthorizationResult
AuthorizationQuery.resolveScope(...) → AuthorizationScope

### 6.4 Eventos

UserProvisioned

UserDeactivated

## 7. Department — estrutura organizacional, orçamento e ModelPolicy

### 7.1 Estrutura organizacional

Department
├── DepartmentId
├── TenantId
├── Name
└── Status

Area
├── AreaId
├── TenantId
├── DepartmentId
├── Name
└── Status

UserAreaMembership
├── UserId
├── DepartmentId
├── AreaId
├── AssignedAt
└── RemovedAt?

IAM armazena apenas DepartmentId no User. A associação User-Area pertence a department e pode permitir múltiplas Areas dentro do mesmo Department.

Transferência de Department é uma operação consistente: antes de alterar User.DepartmentId no IAM, todas as UserAreaMemberships ativas do Department anterior devem receber RemovedAt. Memberships no novo Department são atribuídas explicitamente em operação posterior; nenhuma Area é herdada automaticamente.

### 7.2 OrganizationQuery

OrganizationQuery.getUserOrganization(tenantId, userId)
→ UserOrganizationContext { departmentId, areaIds[] }

### 7.3 BudgetLimit

BudgetLimit é um Value Object do Department, mensal, identificado por ano + mês. Department é a fonte de verdade da meta; observability calcula realizado, percentual e alertas.

BudgetLimit { amount, currency, BudgetPeriod(year, month) }
Invariante: um BudgetLimit por Department + período

### 7.4 ModelPolicy

ModelPolicy
├── allowedModelIds[]
└── defaultModelId?

allowedModelIds não possui duplicatas.

Se allowedModelIds estiver vazio, defaultModelId é nulo.

Se houver ao menos um modelo permitido, defaultModelId é obrigatório.

defaultModelId deve pertencer a allowedModelIds.

ModelPolicy pertence a department. O ai-gateway consulta e aplica a política; não é sua fonte de verdade.

## 8. Project

Project
├── ProjectId
├── TenantId
├── DepartmentId?
├── ProjectName
├── ProjectType
├── StartDate
├── Status: ACTIVE | COMPLETED | ARCHIVED
└── timestamps

Project é propositalmente mínimo e não implementa tarefas, membros, sprints, issues, milestones ou workflow de gestão. DepartmentId é opcional para compatibilidade com o dataset histórico, que não oferece essa relação.

CreateProject

UpdateProject

CompleteProject

ArchiveProject

GetProject

ListProjects

ProjectId pode atravessar o fluxo de execução de IA como dimensão analítica opcional e ser persistido em UsageRecord.

## 9. AI Gateway

### 9.1 Ownership

ai-gateway
├── Provider
├── AITool
├── Model
├── ModelSelection / Routing
└── ProviderCredential references

### 9.2 Distinção entre catálogo

| Conceito | Significado |
| --- | --- |
| Provider | Fornecedor/infraestrutura capaz de executar ou oferecer modelos. |
| AITool | Aplicação/ferramenta de IA utilizada pelo usuário. |
| Model | Modelo específico disponível para execução. |

### 9.3 Provider

Estados ACTIVE/INACTIVE.

Provider inativo não recebe novas execuções.

Desativação não remove histórico.

Configuração concreta fica encapsulada na infraestrutura.

A baseline permite múltiplos Providers em estado ACTIVE simultaneamente. Não existe regra global de “um único provider ativo”. A disponibilidade de uma execução é determinada pela combinação ModelPolicy → Model AVAILABLE → Provider ACTIVE. “Provider local” e “Provider externo” são implementações de infraestrutura; Docker não faz parte da identidade de domínio de um Provider.

### 9.4 Model

Estados AVAILABLE/DISABLED.

Todo Model referencia um ProviderId.

Modelo desabilitado não participa de novas execuções.

Histórico permanece válido após desativação.

### 9.5 AITool

Aggregate independente do catálogo.

Pode referenciar ProviderId quando aplicável.

Estados ACTIVE/INACTIVE.

AILicense referencia AIToolId, mas engagement não é dono do catálogo.

### 9.6 Seleção determinística

requestedModelId?
├── presente → validar que pertence a ModelPolicy.allowedModelIds
└── ausente  → usar ModelPolicy.defaultModelId

Depois: Model AVAILABLE? → Provider ACTIVE? → executar

Não há fallback automático, seleção aleatória, por custo ou por latência no MVP.

### 9.7 Credenciais / BYOK

Credenciais nunca são armazenadas em texto puro. O domínio guarda somente SecretReference/CredentialReference e resolve o segredo por abstração de infraestrutura.

AI request → SelectedModel → Provider → CredentialProvider → Secret Store → Provider Adapter

ConfigureProviderCredential

RotateProviderCredential

RevokeProviderCredential

ValidateProviderCredential

Segredos nunca aparecem em logs, eventos, DTOs públicos, observability ou outros módulos.

### 9.8 Contrato de completion

CompletionCommand
├── userId
├── tenantId
├── departmentId
├── conversationId?
├── messages[]
├── requestedModelId?
└── generationConfigOverride?

CompletionResult
├── content
├── modelId
├── UsageStats
├── estimatedCost
└── latencyMs

O Gateway calcula o custo estimado exatamente uma vez, usando uso efetivo e pricing do modelo. Observability não recalcula o custo individual.

### 9.9 Falhas

Falhas pré-resposta não geram AIRequestFailed no MVP. Elas resultam em exceção abstrata ao consumidor e logging técnico estruturado na infraestrutura.

## 10. Chat

Conversation
├── ConversationId
├── TenantId
├── UserId
├── DepartmentId (snapshot)
├── Title?
├── Status: ACTIVE | ARCHIVED | DELETED
├── LastMessageAt
└── MessageCount

Message
├── MessageId
├── ConversationId
├── Role
├── Content
├── ModelId?
└── CreatedAt

Message é persistido separadamente para paginação, mas permanece no mesmo módulo. Conversation.DepartmentId é snapshot do departamento do usuário no momento da criação.

### 10.1 Privacidade

Somente o dono da conversa pode visualizar conteúdo, enviar mensagens, arquivar ou excluir. MANAGER e ADMIN não recebem acesso ao conteúdo por hierarquia ou administração. Métricas agregadas ficam em observability.

### 10.2 Fluxo

User → Chat → IAM authorization → load recent history → AI Gateway → CompletionResult → persist USER/ASSISTANT messages

Chat não publica UsageRecord nem evento de consumo. O Gateway é a única fonte do evento de uso.

### 10.3 Retenção

DELETE é soft delete no MVP. Purge automático, legal hold e políticas configuráveis de retenção ficam fora desta baseline.

## 11. Agent Policies

### 11.1 Aggregates

KnowledgeDocument
└── DocumentVersion[] / metadata

Agent
└── configuração e instruções

AgentExecution
└── contexto mínimo de uma execução

### 11.2 KnowledgeDocument

O sistema aceita inicialmente DOCX, processa o conteúdo e persiste o texto processado. DocumentVersion possui ciclo DRAFT → PENDING_APPROVAL → ACTIVE → SUPERSEDED. No máximo uma versão pode estar ACTIVE por KnowledgeDocument. Somente a versão ACTIVE é elegível para KnowledgeAccess.

Create document — ADMIN

Create version — ADMIN

Submit for approval — ADMIN

Approve version — ADMIN

Archive — ADMIN

Leitura depende de autenticação, mesmo tenant, versão aprovada e correspondência com ao menos um AccessScope.

AccessScope = DepartmentId OR AreaId
Múltiplos scopes usam semântica OR

Lifecycle de versão: DRAFT → PENDING_APPROVAL → ACTIVE → SUPERSEDED. Aprovar uma nova versão torna a versão ACTIVE anterior SUPERSEDED de forma atômica.

### 11.3 Knowledge Access abstraction

RAG não é uma dependência obrigatória do domínio. Agent Policies depende apenas de uma abstração de acesso ao conhecimento, podendo ser implementada por busca simples, RAG ou outra estratégia.

KnowledgeAccessService.retrieve(request) → KnowledgeResult
Implementação concreta: opcional

### 11.4 Agent

Estados DRAFT/ACTIVE/INACTIVE.

Agent não escolhe provider.

Modelo efetivamente utilizado é resolvido pelo AI Gateway conforme ModelPolicy/default.

A configuração de conhecimento é abstrata.

### 11.5 AgentExecution

Uma pergunta corresponde a uma AgentExecution. O estado mínimo é RUNNING → COMPLETED | FAILED. O fluxo realiza no máximo uma chamada ao AI Gateway no MVP. Conteúdo não é enviado a observability.

User → authorization → Agent ACTIVE → KnowledgeAccess? → AI Gateway → CompletionResult → finalize AgentExecution

AgentExecutionCompleted não é evento obrigatório do MVP. Ele só deve ser introduzido se surgir consumidor explícito para métricas específicas de Agent e, mesmo nesse caso, nunca poderá criar um segundo UsageRecord.

## 12. Observability / FinOps

### 12.1 UsageRecord

UsageRecord
├── UsageRecordId
├── RequestId
├── TenantId
├── UserId
├── DepartmentId
├── AIToolId?
├── ModelId
├── ProviderId
├── ProjectId?
├── UsageOrigin: CHAT | AGENT
├── UsageStats
├── EstimatedCost
└── OccurredAt

UsageRecord é um fato histórico imutável criado exclusivamente a partir de AIRequestCompleted. RequestId deve ser único para garantir idempotência.

### 12.2 Evento de entrada

AI Gateway → AIRequestCompleted → Observability → UsageRecord

O evento inclui metadados operacionais, tokens, latência e custo, mas nunca prompt, response, documento ou segredo.

### 12.3 Métricas

requests

input/output/total tokens

total cost

average cost per request

average latency

uso por modelo

uso por department

uso por user

uso por project

timeline diária/mensal

#### 12.3.1 Histórico analítico

O dataset histórico da QSOFT não é convertido artificialmente em UsageRecord operacional. HistoricalUsageAdapter consulta tabelas historical_* e preserva a semântica original de request_count, status, billing, cost_center e demais campos. Consumidores analíticos podem compor dados operacionais e históricos por Query Ports/read models explícitos.

### 12.4 BudgetStatus

Observability consulta BudgetLimit em department e combina com ActualCost. Não altera orçamento e não recalcula custo individual.

usagePercentage = actualCost / budgetLimit × 100
remainingAmount = budgetLimit - actualCost

### 12.5 Alert

Alert
├── DepartmentId
├── BudgetPeriod
├── AlertType
├── Threshold
├── BudgetAmount
├── ActualCost
├── UsagePercentage
└── Status: OPEN → ACKNOWLEDGED

Thresholds fixos do MVP: 80% gera BUDGET_THRESHOLD_REACHED e 100% gera BUDGET_EXCEEDED. Cada threshold gera no máximo um alerta por Department + período.

### 12.6 Autorização

Observability recebe AuthorizationScope do IAM e aplica filtros no banco. Collaborator vê dados próprios; líderes/gestores veem apenas escopos permitidos; Executive/Admin podem acessar agregações do tenant.

## 13. Engagement

### 13.1 Ownership

engagement
├── AILicense
├── TrainingRecord
├── métricas de adoção
└── AI Credits (derivado)

### 13.2 AILicense

AILicense
├── AILicenseId
├── TenantId
├── UserId
├── AIToolId
├── Status: ACTIVE | REVOKED
├── AssignedAt/By
└── RevokedAt/By?

Somente ADMIN atribui ou revoga licenças. Uma licença ativa não equivale automaticamente a autorização de uso; ela representa disponibilidade/licenciamento. Existe no máximo uma AILicense ACTIVE por TenantId + UserId + AIToolId.

### 13.3 TrainingRecord

TrainingRecord
├── UserId
├── TrainingIdentifier
├── Status: ENROLLED → COMPLETED
└── CompletedAt?

Somente ADMIN cria/atualiza registros. O módulo não é um LMS e não modela conteúdo, provas ou currículo.

### 13.4 Adoção

Um usuário é ACTIVE para engajamento quando possui ao menos uma utilização de IA nos últimos 30 dias corridos. O estado é derivado, não persistido como estado permanente.

AdoptionRate = ActiveUsers / EligibleUsers

TrainingCompletionRate = UsersWithCompletedTraining / EligibleUsers

LicenseUtilizationRate = ActiveLicenseAndUsage / ActiveLicense

TrainedActiveRate = TrainedUsersWithUsage / CompletedTrainingUsers

EligibleUsers é obtido exclusivamente via IAM Query Port, evitando projeção local de identidade. Para análises históricas, HistoricalEngagementAdapter lê licenças/treinamentos históricos sem transformar esses registros em aggregates operacionais.

### 13.5 AI Credits

AI Credits (0–100)
├── Usage: 50 pontos
├── Training: 30 pontos
└── License utilization: 20 pontos

Uso possui saturação para evitar incentivo a desperdício. Dimensão de licença pode ser NOT_APPLICABLE, caso em que o score é normalizado pelo máximo aplicável. AI Credits nunca bloqueia acesso.

## 14. Governance Score

governance-score é um módulo read-oriented que consome Query Ports de outros módulos, normaliza indicadores e calcula um score organizacional determinístico. Não acessa repositories externos e não utiliza LLM.

### 14.1 Dimensões e pesos

| Dimensão | Peso | Fonte principal |
| --- | --- | --- |
| Governance | 40% | Métricas de conformidade/governança |
| Adoption | 20% | engagement |
| Training | 20% | engagement |
| Utilization | 20% | observability + BudgetStatus |

FinalScore = Governance×0.40 + Adoption×0.20 + Training×0.20 + Utilization×0.20

### 14.2 Ausência de dados

Dimensão sem dados suficientes recebe score 0 sem redistribuição de peso. O breakdown deve marcar DataStatus = INSUFFICIENT_DATA para diferenciar ausência de evidência de desempenho real igual a zero.

### 14.3 Governance dimension

GovernanceScore = ApprovedUsageRate×0.40 + PolicyComplianceRate×0.40 + SecurityComplianceRate×0.20

SecurityComplianceRate é proporcional ao volume de atividade: (1 - violatingSecurityEvents / totalAIRequests) × 100, com resultado limitado ao intervalo [0,100]. Ausência de atividade resulta em INSUFFICIENT_DATA.

### 14.4 Adoption e Training

AdoptionScore = AdoptionRate
TrainingScore = TrainingCompletionRate

Para Tenant, os valores-base são agregados antes de calcular a taxa; não se usa média simples entre departamentos.

### 14.5 Utilization

| Budget usage | Utilization Score |
| --- | --- |
| 0% a 80% | 100 |
| 80% a 100% | queda linear de 100 para 50 |
| 100% a 120% | queda linear de 50 para 0 |
| >= 120% | 0 |

Utilization mede sustentabilidade financeira, não volume bruto de requests/tokens. Sem orçamento: score 0 e INSUFFICIENT_DATA.

#### 14.5.1 Período e snapshot

ScorePeriod é YearMonth. Governance e Utilization usam o mês-calendário; Adoption utiliza janela móvel de 30 dias encerrada no último instante do período; Training considera o estado conhecido até o encerramento do mês. O resultado mensal é persistido como GovernanceScoreSnapshot imutável após fechamento, identificado também por CalculationVersion para permitir evolução futura da fórmula sem reescrever histórico.

HistoricalGovernanceAdapter pode fornecer métricas do dataset histórico mantendo separação do domínio operacional. Ausência de uma fonte operacional/histórica necessária continua sendo representada como score 0 + INSUFFICIENT_DATA, sem redistribuição de pesos.

### 14.6 Escopo e autorização

O MVP suporta Score por TENANT e DEPARTMENT. Líderes e gestores veem apenas escopos autorizados; Executive/Admin podem ver o tenant. Score individual por colaborador não faz parte do MVP.

## 15. Mapa definitivo de dependências síncronas

| Consumidor | Dependência | Motivo |
| --- | --- | --- |
| chat | iam | identidade/autorização |
| chat | ai-gateway | completion |
| agent-policies | iam | identidade/autorização |
| agent-policies | department | escopos organizacionais |
| agent-policies | ai-gateway | completion |
| department | iam | validar User em UserAreaMembership |
| project | iam | autorização |
| project | department | validar Department opcional |
| ai-gateway | department | consultar ModelPolicy |
| observability | iam | AuthorizationScope |
| observability | department | BudgetLimit mensal |
| engagement | iam | identidade/autorização/população elegível |
| engagement | observability | métricas de utilização |
| engagement | ai-gateway | AIToolCatalogQuery |
| governance-score | iam | AuthorizationScope |
| governance-score | engagement | adoção/treinamento |
| governance-score | observability | uso/custo/orçamento |
| governance-score | fontes de governança | métricas de conformidade |

## 16. Eventos definitivos

| Produtor | Evento | Consumidor / uso |
| --- | --- | --- |
| iam | UserProvisioned | engagement — população/elegibilidade |
| iam | UserDeactivated | engagement — excluir da população elegível |
| ai-gateway | AIRequestCompleted | observability — única fonte de UsageRecord |

AIRequestFailed não existe no MVP. Falhas de provider ficam em logging técnico estruturado.

## 17. Fluxos end-to-end

### 17.1 Chat

User
 ↓
Chat → IAM authorization
 ↓
Conversation + recent messages
 ↓
AI Gateway → ModelPolicy → Model → Provider
 ↓
CompletionResult
 ↓
Chat persists messages

AI Gateway ──AIRequestCompleted──→ Observability

### 17.2 Agent

User
 ↓
Agent Policies
 ├─ IAM
 ├─ OrganizationQuery
 ├─ KnowledgeAccess (abstrato)
 └─ Agent configuration
 ↓
AI Gateway
 ↓
CompletionResult
 ↓
AgentExecution

AI Gateway ──AIRequestCompleted──→ Observability

### 17.3 Analítico

Observability ──usage metrics──→ Engagement
Engagement ──adoption/training──┐
Observability ──budget/usage────┼─→ Governance Score
Governance sources ────────────┘

## 18. Multi-tenancy e segurança

A aplicação utiliza PostgreSQL compartilhado com tenant_id e TenantContext. Hibernate @Filter pode ser usado como defesa transversal, mas operações sensíveis devem continuar tenant-aware explicitamente.

Todo dado empresarial é tenant-aware ou inequivocamente subordinado a entidade tenant-aware.

TenantId deve vir do contexto autenticado, não ser confiado do frontend.

Queries nativas devem conter filtro tenant_id explicitamente.

Caso RAG/pgvector seja implementado, VectorStore/consulta nativa deve filtrar tenant_id e ter teste com dois tenants.

Testes de integração devem provar que Tenant A não lê dados do Tenant B.

Conteúdo privado não entra em observability, engagement ou governance-score.

### 18.1 Segurança HTTP/JWT

A API é stateless e recebe o token exclusivamente por Authorization: Bearer. TenantContext só é estabelecido depois da validação criptográfica do JWT. CSRF fica desabilitado para a API REST stateless; CORS usa allowlist explícita. HTTPS é obrigatório fora do ambiente local. JWT não deve ser aceito em query string/URL.

O endpoint de login possui rate limiting e usa mensagem genérica para credenciais inválidas, evitando enumeração de tenant/usuário. Logs técnicos devem ser sanitizados e nunca conter senha, JWT completo, segredo de provider, prompt, resposta ou documento privado.

## 19. Persistência modular

Os módulos compartilham a instância física do PostgreSQL, mas não o modelo de persistência. Cada módulo possui suas próprias tabelas/repositories e referencia conceitos externos somente por IDs.

Proibido:
@ManyToOne DepartmentEntity em UsageRecordEntity
Repository de outro módulo
JOIN JPA entre Aggregates cross-module

Permitido:
private UUID departmentId;
Query Port para composição de dados

A composição cross-module é feita em application/query services, nunca por acoplamento de entidades.

Não existem foreign keys SQL entre tabelas pertencentes a módulos distintos; FKs são permitidas apenas dentro do ownership de um módulo. Tabelas de negócio consultadas independentemente carregam tenant_id explícito quando necessário. Flyway é o mecanismo oficial de evolução do schema; Hibernate valida o modelo e não utiliza ddl-auto=update como estratégia de produção.

Valores monetários usam BigDecimal/NUMERIC acompanhados de currency. Instantes usam Instant/TIMESTAMPTZ em UTC. O histórico importado permanece em tabelas historical_* no mesmo PostgreSQL, sem FKs para tabelas operacionais, acessado apenas por Historical adapters/read models.

### 19.1 Estratégia de execução e ambientes

PostgreSQL é o único banco de dados suportado pela aplicação. A diferença entre os modos abaixo é exclusivamente operacional: o código, as migrations Flyway, o modelo relacional e as regras de persistência permanecem os mesmos.

Modo local: Spring Boot executado diretamente pela IDE/Maven e PostgreSQL instalado como serviço local. Docker não é necessário para o desenvolvimento cotidiano.

Modo Docker: Docker Compose sobe o mesmo backend Spring Boot e uma instância PostgreSQL em containers, oferecendo ambiente reproduzível para integração, demonstração, CI/CD e deploy.

H2 não é um banco alternativo oficial do projeto. A decisão evita diferenças de comportamento em Flyway, queries nativas, tipos PostgreSQL, constraints, índices e testes de isolamento multi-tenant.

A existência do Dockerfile/docker-compose.yml não transforma os módulos do Monólito Modular em containers independentes. Quando Docker for utilizado, o backend continua sendo um único processo lógico da aplicação.

Desenvolvimento local: Spring Boot → PostgreSQL local
Ambiente reproduzível: Docker Compose → [Spring Boot] + [PostgreSQL]

## 20. Regras de transação

Uma transação de banco deve permanecer dentro de um módulo sempre que possível.

Chamadas síncronas entre módulos não criam uma transação distribuída.

Eventos são consequências pós-operação, não mecanismo para obter resposta necessária ao caso de uso atual.

AI Gateway completion é síncrono porque Chat/Agent precisam da resposta para concluir o caso de uso.

Observability é efeito secundário do AIRequestCompleted e não deve transformar uma completion válida em falha do usuário.

## 21. Contratos públicos principais

| Módulo | Contratos |
| --- | --- |
| iam | IdentityQuery; AuthorizationQuery |
| department | OrganizationQuery; DepartmentBudgetQuery; DepartmentModelPolicyQuery |
| project | ProjectQueryPort |
| ai-gateway | AICompletionService; AIToolCatalogQuery; catálogo administrativo |
| chat | Conversation/Message commands and queries |
| agent-policies | KnowledgeDocument commands/queries; KnowledgeAccessService; AgentExecutionService |
| observability | UsageMetricsQueryPort; CostMetricsQueryPort; BudgetStatusQueryPort; AlertQueryPort |
| engagement | LicenseQueryPort; TrainingQueryPort; EngagementMetricsQueryPort; AICreditsQueryPort |
| governance-score | GovernanceScoreQueryPort; Breakdown; Department scores; History |

### 21.1 API REST pública

Endpoints públicos seguem /api/v1/{resource} e são implementados por controllers pertencentes ao módulo dono do caso de uso. REST adapters chamam application use cases; Command/Query Ports internos não são expostos automaticamente como HTTP. O AI Gateway não possui endpoint público genérico /ai/complete.

TenantId de operações protegidas vem de JWT/TenantContext, nunca de parâmetro confiado do cliente. Listagens usam page + size zero-based, com default 20 e máximo 100, retornando PaginatedResponse. O login é /api/v1/auth/login e recebe tenantSlug + email + password.

## 22. Regras de autorização consolidadas

| Contexto | Regra |
| --- | --- |
| Chat | Conteúdo acessível somente ao dono da Conversation. |
| KnowledgeDocument admin | Create/version/submit/approve/archive somente ADMIN. |
| KnowledgeDocument read | Mesmo tenant + versão aprovada + match em qualquer Department/Area scope. |
| AI catalog/providers/models | Administração somente ADMIN. |
| Model usage | Usuário autenticado + ModelPolicy do Department + Model/Provider disponíveis. |
| AILicense/Training mutations | Somente ADMIN. |
| Observability | Escopo derivado de AuthorizationScope do IAM. |
| Governance Score | Organizacional; Leader/Manager conforme escopo, Executive/Admin tenant. |

## 23. Testes arquiteturais obrigatórios

### 23.1 ArchUnit — fronteiras estáticas

ArchUnit faz parte do build e falha quando uma dependência não declarada atravessa módulos. Apenas a superfície module.api.* pode ser importada por outro módulo; imports de domain, application interna, infrastructure, repository, entity, controller ou adapter de outro módulo são proibidos. Ciclos são proibidos.

shared-kernel → nenhum módulo de negócio
iam → shared-kernel
department → shared-kernel, iam
project → shared-kernel, iam, department
ai-gateway → shared-kernel, department
chat → shared-kernel, iam, ai-gateway
agent-policies → shared-kernel, iam, department, ai-gateway
observability → shared-kernel, iam, department
engagement → shared-kernel, iam, observability, ai-gateway
governance-score → shared-kernel, iam, engagement, observability

Regras de camada: shared-kernel não depende de módulos de negócio; domain não depende de Spring, JPA, SDK de provider, application ou infrastructure; não existem JPA associations cross-module.

### 23.2 Testes de integração — PostgreSQL real

Testes de integração devem executar contra PostgreSQL, aplicar Flyway do zero e validar constraints, queries nativas tenant-aware e contratos entre módulos. H2 não é usado como substituto oficial do banco nos testes de integração.

Casos obrigatórios: Tenant A não lê/escreve dados do Tenant B; Tenant/User INACTIVE bloqueiam acesso mesmo com JWT ainda válido; RequestId duplicado não gera segundo UsageRecord; transferência de Department encerra memberships antigas; aprovação de DocumentVersion mantém no máximo uma ACTIVE; Chat continua privado inclusive perante MANAGER/ADMIN.

### 23.3 Testes de API e segurança

Cobrir autenticação tenantSlug + email + password, expiração/validação JWT, ausência de tenant_id confiado do cliente, ownership, AuthorizationScope, paginação e respostas de erro sem vazamento de informação. Selenium pode complementar fluxos de interface, mas não substitui ArchUnit nem os testes de integração e segurança do backend.

## 24. Decisões de escopo do MVP

| Incluído | Deliberadamente fora do MVP |
| --- | --- |
| Modular Monolith | Microservices |
| JWT + revalidação de status | SSO corporativo avançado |
| Providers externos/locais abstratos | Fixação em Ollama/vLLM/Kubernetes/GPU ou associação de Provider local a Docker |
| KnowledgeAccess abstrato | RAG obrigatório/embeddings obrigatórios |
| Busca de conhecimento opcional | Hybrid search sofisticado |
| Soft delete em Chat | Política complexa de legal hold/retenção |
| Thresholds fixos 80%/100% | Motor configurável de alertas |
| Governance Score determinístico | LLM julgando conformidade |
| AI Credits analítico | Gamificação com saldo/recompensa/bloqueio |
| SQL agregado em dashboards | CQRS/read-model assíncrono generalizado |

### 24.1 Estrutura física do Monólito Modular

A implementação usa um único projeto Maven. Os dez módulos são packages Java de primeiro nível, protegidos por ArchUnit; não são Maven submodules nem serviços independentes. platform concentra apenas bootstrap/configuração técnica transversal e não contém regras de negócio.

br.com.qsoft.governance
├── sharedkernel
├── iam
├── department
├── project
├── aigateway
├── chat
├── agentpolicies
├── observability
├── engagement
├── governancescore
├── platform
└── GovernanceApplication.java

Cada módulo de negócio:
module/
├── api/{command,query,event,model}
├── domain/{model,service,exception}
├── application/{command,query,service,port}
└── infrastructure/{persistence,web,config,adapters}

Dependência interna preferencial: infrastructure → application → domain. Outro módulo importa somente module.api.*. Flyway permanece no mesmo projeto e as migrations são organizadas por ownership lógico do módulo.

## 25. Índice de ADRs formais

ADR-01 — Modular Monolith

ADR-02 — Cross-module Communication

ADR-03 — Multi-tenancy

ADR-04 — IAM

ADR-05 — User Area Ownership

ADR-06 — Budget Ownership

ADR-07 — ModelPolicy Ownership

ADR-08 — Mandatory Default Model

ADR-09 — AI Catalog

ADR-10 — Provider Credentials

ADR-11 — AI Completion

ADR-12 — AI Failure Handling

ADR-13 — Usage Observability

ADR-14 — Knowledge Access Abstraction

ADR-15 — KnowledgeDocument Authorization

ADR-16 — Chat Privacy

ADR-17 — Engagement

ADR-18 — Adoption Window

ADR-19 — AI Credits

ADR-20 — Governance Score

ADR-21 — Missing Score Data

ADR-22 — Security Compliance

ADR-23 — Utilization

ADR-24 — Project

ADR-25 — Agent Policies Lifecycle

ADR-26 — Operational and Historical Analytics Separation

ADR-27 — Governance Score Period and Snapshot

ADR-28 — REST API Design

ADR-29 — Modular Persistence

ADR-30 — Authentication and JWT Security

ADR-31 — Physical Modular Structure and Enforcement

## 26. Baseline resultante

Com as decisões acima, a baseline do MVP está fechada em domínio, integração, segurança, persistência, API, estrutura física, ambientes e testes arquiteturais. PostgreSQL é o único banco suportado e pode executar localmente ou via Docker Compose; o AI Gateway mantém Provider/AITool/Model separados e suporta múltiplos Providers ACTIVE; autenticação usa tenantSlug + email + password e JWT stateless; histórico analítico permanece separado do estado operacional; e a estrutura física é um único Maven com fronteiras module.api.* protegidas por ArchUnit. Os 31 ADRs abaixo registram formalmente essas escolhas.

| Módulo | Estado |
| --- | --- |
| IAM | FECHADO |
| Department | FECHADO |
| Project | FECHADO |
| AI Gateway | FECHADO |
| Chat | FECHADO |
| Agent Policies | FECHADO |
| Observability | FECHADO |
| Engagement | FECHADO |
| Governance Score | FECHADO |

## 27. ADRs formais

Os registros abaixo são a forma normativa das decisões arquiteturais da baseline. Todos estão em status ACCEPTED. O índice da Seção 25 preserva a navegação e a numeração histórica; esta seção contém contexto, decisão, alternativas e consequências de cada registro.

### ADR-01 — Modular Monolith

Status: ACCEPTED

Contexto: O MVP precisa demonstrar modularidade, baixo acoplamento e responsabilidade clara sem assumir o custo operacional de microservices.

Decisão: Implementar um único backend Java/Spring Boot como Monólito Modular, com um deploy lógico e PostgreSQL compartilhado. As fronteiras de módulo são explícitas e independentes de processo.

Alternativas consideradas: Microservices desde o início; monólito sem fronteiras.

Consequências: Simplifica deploy e transações locais, mas exige disciplina de dependências e testes arquiteturais para impedir erosão modular.

### ADR-02 — Cross-module Communication

Status: ACCEPTED

Contexto: Módulos precisam colaborar sem compartilhar entidades, repositories ou services internos.

Decisão: Comunicação cross-module ocorre somente por Command Port, Query Port ou Domain Event expostos em module.api.*. Dependências seguem allowlist explícita e ciclos são proibidos.

Alternativas consideradas: Imports diretos; banco compartilhado como integração; eventos para qualquer consulta síncrona.

Consequências: Contratos ficam rastreáveis e testáveis. Mudanças internas permanecem encapsuladas, ao custo de DTOs/ports explícitos.

### ADR-03 — Multi-tenancy

Status: ACCEPTED

Contexto: A plataforma white-label atende múltiplas empresas em uma mesma aplicação e banco.

Decisão: Usar tenant_id + TenantContext transversalmente. TenantId vem do contexto autenticado; queries nativas e históricos tenant-aware filtram explicitamente. Testes com pelo menos dois tenants são obrigatórios.

Alternativas consideradas: Banco por tenant; schema por tenant; confiar tenant_id enviado pelo frontend.

Consequências: Reduz custo operacional, porém qualquer nova persistência/query precisa comprovar isolamento e evitar vazamento entre tenants.

### ADR-04 — IAM

Status: ACCEPTED

Contexto: Identidade, hierarquia e autorização não podem ser reimplementadas em cada módulo.

Decisão: IAM é dono de Tenant/User, autenticação, HierarchyLevel, Permissions e AuthorizationScope. ADMIN é conjunto de permissões, não nível hierárquico. Outros módulos consultam IdentityQuery/AuthorizationQuery.

Alternativas consideradas: RBAC espalhado por controllers; ADMIN como cargo; autorização delegada ao frontend.

Consequências: Centraliza regras de segurança e facilita revogação. Módulos de negócio dependem de contratos IAM em vez de conhecer sua persistência.

### ADR-05 — User Area Ownership

Status: ACCEPTED

Contexto: User pertence a um Department, mas pode atuar em múltiplas Areas do mesmo Department.

Decisão: IAM armazena DepartmentId no User; department é dono de UserAreaMembership. Transferir Department encerra todas as memberships ativas antigas antes da mudança; novas Areas são atribuídas explicitamente.

Alternativas consideradas: Area dentro de IAM; associação User-Area many-to-many cross-module; herança automática de Areas.

Consequências: Evita duplicação de estrutura organizacional e deixa o ciclo de membership auditável.

### ADR-06 — Budget Ownership

Status: ACCEPTED

Contexto: Orçamento é meta organizacional; custo realizado é fato de observabilidade.

Decisão: Department é fonte de verdade de BudgetLimit mensal. Observability é dono de ActualCost, BudgetStatus e Alert e consulta o orçamento por port.

Alternativas consideradas: Budget dentro de Observability; cópia local de orçamento nos dois módulos.

Consequências: Separa meta de realizado e evita duas fontes de verdade. Cálculo analítico depende de integração síncrona clara.

### ADR-07 — ModelPolicy Ownership

Status: ACCEPTED

Contexto: Política de modelos é uma regra organizacional do Department, mas a execução pertence ao AI Gateway.

Decisão: ModelPolicy pertence a department; ai-gateway valida IDs no catálogo, consulta e aplica allowedModelIds/defaultModelId. Administração orquestra validação sem criar ciclo Department→Gateway.

Alternativas consideradas: ModelPolicy dentro do Gateway; duplicar políticas em ambos.

Consequências: Mantém ownership organizacional e catálogo técnico separados, exigindo contrato explícito para validação dos ModelIds.

### ADR-08 — Mandatory Default Model

Status: ACCEPTED

Contexto: O routing precisa ser determinístico quando o usuário não solicita modelo.

Decisão: Se allowedModelIds não estiver vazio, defaultModelId é obrigatório e deve pertencer ao conjunto permitido. Conjunto vazio implica default nulo.

Alternativas consideradas: Escolha aleatória; primeiro modelo da lista; ausência de default.

Consequências: Elimina ambiguidades de execução e torna configuração inválida detectável antes da completion.

### ADR-09 — AI Catalog

Status: ACCEPTED

Contexto: Dataset e provedores externos misturam conceitos de ferramenta, fornecedor e modelo que precisam ser separados no domínio.

Decisão: ai-gateway possui Provider, AITool e Model como conceitos distintos. Vários Providers podem estar ACTIVE simultaneamente; cada Model referencia um Provider.

Alternativas consideradas: Provider=Model; AITool=Model; único provider ativo global.

Consequências: Suporta múltiplos fornecedores e ferramentas sem acoplar políticas/licenças ao SDK específico.

### ADR-10 — Provider Credentials

Status: ACCEPTED

Contexto: Credenciais BYOK são sensíveis e não podem circular pelo domínio ou observabilidade.

Decisão: Domínio persiste apenas SecretReference/CredentialReference. CredentialProvider resolve o segredo via Secret Store na infraestrutura. Segredos não aparecem em logs, eventos ou DTOs públicos.

Alternativas consideradas: Chave em tabela em texto puro; chave em application.yml; chave enviada ao frontend.

Consequências: Reduz superfície de exposição e permite trocar tecnologia de Secret Store sem alterar domínio.

### ADR-11 — AI Completion

Status: ACCEPTED

Contexto: Chat e Agent precisam de resposta do modelo para concluir o mesmo caso de uso.

Decisão: AICompletionService do ai-gateway é síncrono. requestedModelId é opcional e, quando presente, deve estar em ModelPolicy.allowedModelIds; caso contrário usa default. Depois valida Model AVAILABLE e Provider ACTIVE.

Alternativas consideradas: Fila assíncrona obrigatória; endpoint público genérico; routing por custo/latência.

Consequências: Fluxo do usuário permanece simples e determinístico. Provider concreto fica encapsulado em adapter.

### ADR-12 — AI Failure Handling

Status: ACCEPTED

Contexto: Falhas antes de existir completion não representam uso bem-sucedido e não devem poluir analytics de produto.

Decisão: Falhas pré-resposta retornam exceção abstrata ao consumidor e logging técnico estruturado. AIRequestFailed não existe no MVP.

Alternativas consideradas: Criar UsageRecord de falha; publicar evento de domínio para toda falha de SDK.

Consequências: Separa observabilidade técnica de métricas de produto; troubleshooting depende de logging técnico adequado.

### ADR-13 — Usage Observability

Status: ACCEPTED

Contexto: Uso e custo precisam de uma origem operacional única para evitar dupla contagem entre Chat e Agent.

Decisão: AIRequestCompleted emitido pelo Gateway é a única fonte operacional de UsageRecord. Gateway calcula custo individual uma vez. UsageRecord é imutável, idempotente por RequestId e pode carregar AIToolId.

Alternativas consideradas: Chat/Agent gravam UsageRecord; Observability recalcula custo.

Consequências: Evita duplicidade e divergência financeira. Consumidores aceitam que Observability é efeito secundário da completion.

### ADR-14 — Knowledge Access Abstraction

Status: ACCEPTED

Contexto: RAG pode ser útil, mas não é requisito obrigatório do MVP nem deve contaminar o domínio.

Decisão: Agent Policies depende apenas de KnowledgeAccessService. Implementação pode ser busca simples, RAG ou outra estratégia, sempre tenant/scope-aware.

Alternativas consideradas: pgvector obrigatório; SDK de RAG dentro do domínio.

Consequências: Permite começar simples e evoluir retrieval sem alterar casos de uso ou autorização.

### ADR-15 — KnowledgeDocument Authorization

Status: ACCEPTED

Contexto: Documentos corporativos podem ter escopo por Department/Area e exigem administração controlada.

Decisão: Create/version/submit/approve/archive são ADMIN-only. Leitura exige mesmo tenant, versão ACTIVE e correspondência OR com ao menos um Department/Area scope; autorização ocorre antes do retrieval/LLM.

Alternativas consideradas: LLM decide acesso; managers podem ler tudo; AND entre múltiplos scopes.

Consequências: Impede vazamento de conhecimento e preserva política determinística independente do modelo.

### ADR-16 — Chat Privacy

Status: ACCEPTED

Contexto: Conteúdo conversacional pode conter informação sensível e não é necessário para analytics gerenciais.

Decisão: Somente o proprietário pode visualizar, enviar, arquivar ou excluir Conversation/Message. MANAGER/ADMIN não obtêm acesso por hierarquia. Observability recebe somente metadados.

Alternativas consideradas: Administrador com acesso universal; conteúdo copiado para analytics.

Consequências: Fortalece privacidade, mas suporte/auditoria precisa operar sem leitura indiscriminada do conteúdo.

### ADR-17 — Engagement

Status: ACCEPTED

Contexto: Licenças e treinamentos são fatos transacionais; adoção e créditos são métricas derivadas.

Decisão: Engagement é dono de AILicense/TrainingRecord e deriva métricas. EligibleUsers vem apenas de IAM Query Port; existe no máximo uma licença ACTIVE por tenant+user+AITool. Histórico usa HistoricalEngagementAdapter.

Alternativas consideradas: Projeção local de usuários; múltiplas licenças ativas iguais; transformar histórico em entidades operacionais.

Consequências: Evita drift de identidade e mantém histórico separado, ao custo de depender de queries de outros módulos para cálculo.

### ADR-18 — Adoption Window

Status: ACCEPTED

Contexto: “Usuário ativo” precisa de uma janela única e reproduzível.

Decisão: Usuário ativo para Engagement possui ao menos uma utilização nos últimos 30 dias corridos. No Governance mensal, a janela termina no fechamento do ScorePeriod.

Alternativas consideradas: Mês calendário apenas; estado ACTIVE persistente; janela configurável no MVP.

Consequências: Comparações tornam-se consistentes; é necessário explicitar data de corte em relatórios.

### ADR-19 — AI Credits

Status: ACCEPTED

Contexto: Gamificação não pode incentivar desperdício nem bloquear IA corporativa, o que poderia aumentar Shadow AI.

Decisão: AI Credits é indicador 0–100 não bloqueante: Usage 50, Training 30, License utilization 20, com saturação de uso e normalização quando licença for NOT_APPLICABLE.

Alternativas consideradas: Crédito consumível; ranking punitivo; bloqueio por saldo; motor de regras configurável no MVP.

Consequências: Métrica permanece simples e explicável. Alterações futuras na fórmula exigem decisão/versionamento explícito.

### ADR-20 — Governance Score

Status: ACCEPTED

Contexto: A organização precisa de um indicador executivo explicável e não dependente de LLM.

Decisão: FinalScore = Governance 40% + Adoption 20% + Training 20% + Utilization 20%. O módulo é read-oriented e consome GovernanceMetricsQueryPort/ports analíticos sem acessar repositories externos.

Alternativas consideradas: Score gerado por IA; média simples de departments; score individual de colaborador.

Consequências: Cálculo é auditável e determinístico, limitado a TENANT/DEPARTMENT no MVP.

### ADR-21 — Missing Score Data

Status: ACCEPTED

Contexto: Ausência de evidência não pode ser confundida com redistribuição silenciosa de pesos.

Decisão: Dimensão sem fonte/dados suficientes recebe score 0 + DataStatus=INSUFFICIENT_DATA. Pesos não são redistribuídos.

Alternativas consideradas: Ignorar dimensão; reponderar automaticamente; usar valor neutro.

Consequências: Score penaliza falta de evidência, mas o breakdown torna explícita a diferença entre ausência de dados e desempenho ruim.

### ADR-22 — Security Compliance

Status: ACCEPTED

Contexto: Eventos de segurança precisam ser normalizados pelo volume de uso para composição do Governance Score.

Decisão: SecurityComplianceRate = (1 - violatingSecurityEvents/totalAIRequests)×100, limitado a [0,100]. Sem atividade suficiente, retorna INSUFFICIENT_DATA.

Alternativas consideradas: Contagem bruta de eventos; permitir valores negativos; score arbitrário por severidade no MVP.

Consequências: Métrica fica comparável entre escopos de tamanhos diferentes e evita resultados fora do domínio esperado.

### ADR-23 — Utilization

Status: ACCEPTED

Contexto: Utilização no score deve medir sustentabilidade financeira, não simplesmente volume de tokens/requests.

Decisão: BudgetHealth: 100 até 80% do orçamento; queda linear 100→50 entre 80–100%; 50→0 entre 100–120%; 0 em 120% ou acima. Sem orçamento: 0 + INSUFFICIENT_DATA.

Alternativas consideradas: Mais uso = score maior; bloqueio ao exceder orçamento; thresholds configuráveis no MVP.

Consequências: Incentiva previsibilidade de custo sem bloquear usuários e mantém regra transparente.

### ADR-24 — Project

Status: ACCEPTED

Contexto: Project aparece nos dados e é útil como dimensão analítica, mas não há requisito de gestão completa de projetos.

Decisão: Manter módulo Project próprio com cadastro/ciclo de vida mínimo e Department opcional quando aplicável. Não modelar tasks, members, managers ou workflow no MVP.

Alternativas consideradas: Project dentro de Department; módulo completo de gestão de projetos; remover Project.

Consequências: Preserva identidade e segmentação sem inflar escopo.

### ADR-25 — Agent Policies Lifecycle

Status: ACCEPTED

Contexto: Versões de documentos e execuções do agente precisam de estados mínimos para rastreabilidade e consistência.

Decisão: DocumentVersion: DRAFT→PENDING_APPROVAL→ACTIVE→SUPERSEDED, com no máximo uma ACTIVE por documento. AgentExecution: RUNNING→COMPLETED|FAILED. AgentExecutionCompleted só é criado se houver consumidor futuro explícito.

Alternativas consideradas: Documento sem versionamento; múltiplas versões ativas; evento obrigatório sem consumidor.

Consequências: Ciclo fica auditável e evita ambiguidade de conhecimento vigente; aprovação precisa ser atômica.

### ADR-26 — Operational and Historical Analytics Separation

Status: ACCEPTED

Contexto: O dataset QSOFT possui semântica histórica diferente das entidades operacionais criadas pela aplicação.

Decisão: Importar dados históricos em historical_* no PostgreSQL e acessá-los por HistoricalUsageAdapter/HistoricalEngagementAdapter/HistoricalGovernanceAdapter. Não converter histórico artificialmente em aggregates operacionais nem criar FKs para tabelas operacionais.

Alternativas consideradas: Mesclar histórico e operação nas mesmas tabelas; migrar cada linha histórica para entidade de domínio.

Consequências: Preserva significado e permite analytics híbrido, ao custo de adapters/read models adicionais.

### ADR-27 — Governance Score Period and Snapshot

Status: ACCEPTED

Contexto: Score precisa ser reproduzível no tempo mesmo quando fórmulas/dados evoluírem.

Decisão: ScorePeriod=YearMonth. Governance/Utilization usam mês; Adoption usa 30 dias encerrados no fim do período; Training é as-of period-end. Persistir GovernanceScoreSnapshot mensal com CalculationVersion e torná-lo imutável após fechamento.

Alternativas consideradas: Recalcular histórico sempre; período rolling indefinido; snapshot sem versão.

Consequências: Histórico torna-se auditável e comparável; correções retroativas exigem procedimento/versionamento explícito.

### ADR-28 — REST API Design

Status: ACCEPTED

Contexto: A API precisa de convenções estáveis sem expor a arquitetura interna diretamente.

Decisão: Usar /api/v1/{resource}, controllers por módulo e REST→application use case. Tenant vem do contexto autenticado. Login em /api/v1/auth/login. Paginação page/size zero-based, default 20, máximo 100. Não existe /ai/complete público.

Alternativas consideradas: Endpoints por nome de módulo; expor ports internos diretamente; tenant_id como parâmetro confiado.

Consequências: Contrato HTTP fica previsível e desacoplado dos detalhes internos do Monólito Modular.

### ADR-29 — Modular Persistence

Status: ACCEPTED

Contexto: Um único PostgreSQL precisa preservar ownership dos módulos e ser reproduzível em ambientes diferentes.

Decisão: Cada módulo possui tabelas/repositories próprios; FKs apenas intramodule; cross-module por IDs/ports. Flyway gerencia schema. PostgreSQL é o único SGBD suportado. Execução pode ser local (Spring Boot + PostgreSQL local) ou Docker Compose (backend + PostgreSQL); H2 não é banco oficial.

Alternativas consideradas: FKs cross-module; schema gerado por Hibernate update; H2 como banco alternativo oficial; banco por módulo.

Consequências: Mantém semântica real do PostgreSQL em dev/teste e reduz acoplamento. Docker continua opcional no desenvolvimento local.

### ADR-30 — Authentication and JWT Security

Status: ACCEPTED

Contexto: Autenticação multi-tenant precisa identificar a organização antes do usuário e permitir revogação efetiva.

Decisão: Login: tenantSlug+email+password, BCrypt, JWT Bearer stateless de 30 min, sem refresh no MVP. Claims estáveis: sub/UserId, tenant_id, iat/exp. Tenant/User/permissões/escopos são revalidados no IAM. CSRF off para REST stateless, CORS allowlist, HTTPS fora do local, rate limit no login e logs sanitizados.

Alternativas consideradas: Session server-side; email global; roles dentro do token como autoridade; JWT em URL; refresh token no MVP.

Consequências: Reduz estado de sessão e mantém revogação rápida; cada request protegido consulta estado mutável necessário no IAM.

### ADR-31 — Physical Modular Structure and Enforcement

Status: ACCEPTED

Contexto: A arquitetura lógica precisa ser refletida no código sem multiplicar builds/serviços desnecessariamente.

Decisão: Um único projeto Maven com packages de primeiro nível por módulo. Cada módulo organiza api/domain/application/infrastructure; outro módulo importa somente api.*. platform contém bootstrap/config técnica. ArchUnit aplica allowlist, regras de camada e ausência de ciclos.

Alternativas consideradas: Maven multi-module no MVP; package por camada global; confiança apenas em convenção humana.

Consequências: Estrutura é simples para equipe acadêmica e possui enforcement automático; violações arquiteturais falham o build.