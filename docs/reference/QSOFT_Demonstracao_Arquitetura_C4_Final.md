DEMONSTRAÇÃO DA ARQUITETURA
NO MODELO C4

Plataforma de Governança Corporativa de IA — QSOFT

Projeto Integrador IV — Java + Spring Boot + PostgreSQL | Execução local ou Docker Compose

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Arquitetura de referência — Agosto de 2026

## 1. Objetivo e escopo

Este documento apresenta a arquitetura da Plataforma de Governança Corporativa de IA no formato do C4 Model. O objetivo é demonstrar o projeto em níveis progressivos de abstração — Contexto de Sistema, Containers e Componentes — sem substituir ou remodelar a arquitetura já definida como Monólito Modular.

| Escopo: C1, C2 e C3 são tratados como documentação principal. O nível C4-Code é apresentado apenas como exemplo representativo do módulo AI Gateway, pois o detalhamento completo de classes não é necessário para demonstrar a arquitetura do sistema. |
| --- |

A fonte principal é a baseline funcional da arquitetura do projeto. Este documento também incorpora as decisões fechadas na auditoria arquitetural subsequente — especialmente separação operacional/histórica, API REST, segurança JWT, persistência modular, estrutura física de packages e enforcement por ArchUnit. Quando a baseline original não define um elemento físico, ele não é inventado.

### 1.1 Como o C4 se relaciona com o projeto

| Nível | Pergunta respondida | Representação neste projeto |
| --- | --- | --- |
| C1 — System Context | Quem usa o sistema e com quais sistemas externos ele se relaciona? | Plataforma QSOFT, perfis corporativos, AI Providers, Secret Store e fonte histórica. |
| C2 — Container | Quais aplicações executáveis e armazenamentos formam a solução? | Backend Spring Boot e PostgreSQL, mais integrações externas. |
| C3 — Component | Quais componentes relevantes existem dentro do backend? | Os 10 módulos lógicos do Monólito Modular e suas relações. |
| C4 — Code (exemplo) | Como um componente é estruturado internamente? | Exemplo do AI Gateway em api/domain/application/infrastructure. |

### 1.2 Premissas preservadas

Monólito Modular: um único deploy da aplicação no MVP, com limites lógicos fortes entre módulos.

PostgreSQL é o único SGBD suportado; a persistência mantém ownership por módulo e referências cross-module por IDs. O banco pode ser executado localmente ou em Docker sem alterar o modelo C4.

Comunicação entre módulos somente por Command Port, Query Port ou Domain Event.

Autorização determinística antes de qualquer chamada de IA; LLMs não decidem autorização.

Multi-tenancy por TenantContext + tenant_id, com isolamento obrigatório entre tenants.

RAG permanece opcional atrás de KnowledgeAccess; banco vetorial não é requisito do MVP.

## 2. C1 — System Context

No nível C1, a plataforma é tratada como um único sistema. O foco é demonstrar pessoas, responsabilidades de uso e dependências externas, sem expor ainda a estrutura interna do backend.

### 2.1 Pessoas

| Pessoa / perfil | Interação principal |
| --- | --- |
| COLLABORATOR | Utiliza Chat/Agent e acessa dados próprios nos contextos autorizados. |
| LEADER / MANAGER | Consulta métricas e Governance Score dentro de AuthorizationScope. |
| EXECUTIVE | Consulta agregações organizacionais no tenant. |
| ADMIN — conjunto de permissões | Administra recursos administrativos; não representa nível hierárquico adicional. |

### 2.2 Sistemas e fontes externas

AI Providers: recebem completions através dos adapters encapsulados pelo AI Gateway.

Secret Store: resolve segredos a partir de SecretReference/CredentialReference; credenciais não atravessam DTOs, eventos ou módulos analíticos.

Fonte histórica QSOFT: dataset utilizado como fonte analítica histórica separada dos aggregates operacionais.

### 2.3 Diagrama C1 — Contexto do Sistema

O diagrama abaixo demonstra a plataforma como uma única caixa e as relações relevantes no seu entorno.

Figura 1 — C1 System Context da Plataforma QSOFT.

## 3. C2 — Container

No C4, “Container” significa uma unidade executável ou armazenamento de dados, e não um container Docker. A solução possui, no nível C2, um backend Spring Boot e um PostgreSQL. Esses mesmos containers C4 podem ser executados diretamente na máquina local ou empacotados em Docker; a escolha operacional não altera o diagrama lógico.

| Nota sobre frontend: A baseline arquitetural funcional é centrada no backend e não formaliza um frontend próprio como container da solução. Por isso, nenhum Web App é inventado neste diagrama. Caso o projeto passe a possuir uma aplicação frontend formalizada, ela poderá ser adicionada ao C2 sem alterar o desenho interno do backend. |
| --- |

### 3.1 Containers internos

Backend API — Java + Spring Boot: Monólito Modular, endpoints REST /api/v1, autenticação JWT Bearer e coordenação dos casos de uso.

PostgreSQL: armazenamento operacional tenant-aware e tabelas históricas separadas; evolução de schema por Flyway.

### 3.2 Relações externas do container Backend

AI Providers por adapters do AI Gateway.

Secret Store por CredentialProvider.

PostgreSQL por JPA/SQL; queries nativas tenant-aware mantêm filtro tenant_id explícito.

### 3.3 Diagrama C2 — Containers

O diagrama representa somente containers efetivamente definidos na baseline e dependências externas relevantes.

Figura 2 — C2 Container Diagram.

### 3.4 Estratégia de execução (não altera o C4)

A baseline suporta dois modos de execução para o mesmo desenho C2. PostgreSQL continua sendo o único banco suportado; H2 não faz parte da arquitetura oficial.

Desenvolvimento local: Backend Spring Boot executado pela IDE/Maven → PostgreSQL instalado localmente. Esse modo evita a necessidade de iniciar Docker durante o desenvolvimento cotidiano.

Ambiente reproduzível: Docker Compose → container do Backend Spring Boot + container PostgreSQL. É indicado para integração, demonstração, CI/CD e implantação.

Docker é uma estratégia de deployment/empacotamento. Não existe um container Docker por módulo: IAM, Chat, AI Gateway, Observability e os demais continuam componentes internos do mesmo Monólito Modular.

## 4. C3 — Component

O C3 decompõe o container Backend nos componentes arquiteturais mais relevantes. Neste projeto, os componentes correspondem aos módulos do Monólito Modular. Cada módulo possui ownership claro de dados e regras de negócio, e a comunicação cross-module é realizada exclusivamente por contratos públicos ou eventos.

### 4.1 Componentes do backend

| Componente | Responsabilidade central |
| --- | --- |
| shared-kernel | IDs e tipos realmente transversais, DomainEvent, TenantContext e exceções base; não é módulo de negócio. |
| iam | Tenant, User, autenticação, hierarquia, permissões e AuthorizationScope. |
| department | Department, Area, UserAreaMembership, BudgetLimit e ModelPolicy. |
| project | Cadastro e ciclo de vida mínimo de Project. |
| ai-gateway | Provider, AITool, Model, seleção determinística, credenciais e execução de IA. |
| chat | Conversation, Message e privacidade do conteúdo conversacional. |
| agent-policies | KnowledgeDocument, versões, Agent, AgentExecution e acesso autorizado ao conhecimento. |
| observability | UsageRecord, métricas de uso/custo, BudgetStatus e Alert. |
| engagement | AILicense, TrainingRecord, adoção, métricas e AI Credits. |
| governance-score | Normalização e cálculo do AI Adoption & Governance Score, breakdown e histórico. |

### 4.2 Contratos públicos relevantes

| Módulo | Superfície pública principal |
| --- | --- |
| iam | IdentityQuery; AuthorizationQuery |
| department | OrganizationQuery; DepartmentBudgetQuery; DepartmentModelPolicyQuery |
| project | ProjectQueryPort |
| ai-gateway | AICompletionService; AIToolCatalogQuery; catálogo administrativo; AIRequestCompleted |
| chat | Conversation/Message commands and queries |
| agent-policies | KnowledgeDocument commands/queries; KnowledgeAccessService; AgentExecutionService |
| observability | UsageMetricsQueryPort; CostMetricsQueryPort; BudgetStatusQueryPort; AlertQueryPort |
| engagement | LicenseQueryPort; TrainingQueryPort; EngagementMetricsQueryPort; AICreditsQueryPort |
| governance-score | GovernanceScoreQueryPort; Breakdown; Department scores; History |

| Leitura: O diagrama C3 prioriza legibilidade e mostra as relações entre módulos. Os nomes completos dos contratos permanecem na tabela acima; apenas o evento AIRequestCompleted é rotulado diretamente no diagrama. |
| --- |

### 4.3 Diagrama C3 — Componentes do Backend

Setas contínuas representam consultas/chamadas síncronas por contratos públicos; a seta tracejada laranja representa o evento AIRequestCompleted. Relações históricas são pontilhadas.

Figura 3 — C3 Component Diagram do backend modular.

### 4.4 Regras para leitura do C3

A direção de uma relação síncrona indica o consumidor chamando um contrato público do módulo fornecedor.

AIRequestCompleted representa fluxo de evento do AI Gateway para Observability; não é uma chamada RPC assíncrona.

Todos os módulos podem depender apenas dos tipos transversais permitidos do shared-kernel; o shared-kernel nunca depende de módulos de negócio.

HistoricalUsageAdapter, HistoricalEngagementAdapter e HistoricalGovernanceAdapter preservam a separação entre dados históricos e aggregates operacionais.

A superfície cross-module implementável fica restrita a module.api.* e é protegida por allowlist de dependências em ArchUnit.

### 4.5 Fluxos de referência

Chat: User → Chat → IAM authorization → AI Gateway → CompletionResult → Chat persiste mensagens; AI Gateway publica AIRequestCompleted para Observability.

Agent: User → Agent Policies → IAM/OrganizationQuery/KnowledgeAccess → AI Gateway → CompletionResult → AgentExecution; uso operacional continua vindo do evento do AI Gateway.

Analítico: Observability fornece métricas de uso a Engagement; Engagement e Observability alimentam Governance Score juntamente com fontes de governança.

## 5. C4 — Code Example (AI Gateway)

O nível Code é opcional no C4 e, para este projeto, é usado apenas como demonstração. O AI Gateway foi escolhido porque evidencia com clareza a separação api/domain/application/infrastructure, o encapsulamento do provider e a dependência externa de políticas do Department.

### 5.1 Exemplo de estrutura interna do AI Gateway

O diagrama não pretende listar todas as classes do módulo; ele demonstra as responsabilidades e a direção das dependências esperadas na implementação.

Figura 4 — C4-Code representativo do módulo AI Gateway.

No AI Gateway, múltiplos Providers podem permanecer ACTIVE simultaneamente. ModelPolicy.allowedModelIds/defaultModelId determina o modelo permitido; requestedModelId é opcional e precisa pertencer à policy. “Provider local” não significa “container Docker”: Docker é apenas uma possível estratégia de execução da infraestrutura.

### 5.2 Estrutura física de packages

br.com.qsoft.governance.aigateway

api — contratos públicos que podem ser consumidos por outros módulos.

domain — Provider, AITool, Model e regras de domínio sem dependência de Spring/JPA/SDK de provider.

application — casos de uso, seleção determinística, orchestration e ports de saída.

infrastructure — adapters de provider, Secret Store, persistência JPA e adapters REST administrativos.

| Regra de dependência: Infrastructure → Application → Domain. Outro módulo pode importar apenas a superfície pública api.*; imports de domain/application interna/infrastructure de outro módulo são proibidos. |
| --- |

## 6. Restrições arquiteturais transversais

### 6.1 Multi-tenancy

TenantId é derivado do contexto autenticado e não é confiado a partir do frontend.

TenantContext e tenant_id são aplicados transversalmente; SQL nativo tenant-aware mantém filtro explícito.

Dados empresariais consultados independentemente possuem tenant_id próprio quando necessário para isolamento e paginação.

Testes de integração devem provar que Tenant A não lê dados do Tenant B.

### 6.2 Autenticação e autorização

Login do MVP: tenantSlug + email + password; senha verificada com BCrypt.

JWT transmitido por Authorization: Bearer, com validade de 30 minutos e sem refresh token no MVP.

JWT carrega identidade mínima (UserId e TenantId); permissões e escopo permanecem autoridade do estado atual do IAM.

Tenant ACTIVE, User ACTIVE, sameTenant, permission e AuthorizationScope são verificados antes do caso de uso protegido.

Conteúdo de Chat permanece privado ao proprietário, inclusive perante MANAGER e ADMIN.

### 6.3 Persistência e histórico

Cada módulo possui suas próprias tabelas/repositories; cross-module não usa associação JPA nem FK SQL entre módulos.

Referências cross-module são persistidas como IDs e validadas por ports/application services.

Flyway é o mecanismo oficial de evolução do schema.

Dados históricos QSOFT são importados para tabelas historical_* separadas e consultados por adapters históricos; não são convertidos artificialmente em aggregates operacionais.

### 6.4 Enforcement arquitetural

Projeto Maven único; os módulos de negócio são packages Java de primeiro nível.

ArchUnit mantém allowlist explícita de dependências entre módulos e proíbe ciclos.

Domain não depende de Spring, JPA, SDK de provider, application ou infrastructure.

Testes de integração cobrem multi-tenancy, idempotência, constraints e segurança; ArchUnit não substitui testes de comportamento runtime.

### 6.5 Estratégia de testes

Architecture: ArchUnit valida allowlist, module.api.*, regras de camada e ausência de ciclos. Integration: PostgreSQL real + Flyway, multi-tenancy, idempotência e constraints. Security/API: JWT, Tenant/User ACTIVE, ownership, AuthorizationScope e paginação. Selenium é complementar para interface e não substitui essas garantias.

## 7. Rastreabilidade arquitetural

Os diagramas C4 são uma visão da arquitetura existente. Eles não introduzem um novo estilo arquitetural nem substituem os ADRs. A rastreabilidade principal é:

| Elemento C4 | Decisões arquiteturais relacionadas | Efeito no desenho |
| --- | --- | --- |
| C2 Backend único | ADR-01 | Um único container executável para o backend no MVP. |
| Comunicação entre componentes | ADR-02 / ADR-31 | Ports/Event + superfície api.* + enforcement ArchUnit. |
| Tenant isolation | ADR-03 / ADR-30 | TenantContext, tenant_id e autenticação contextualizada. |
| AI Gateway e providers | ADR-09 / ADR-10 / ADR-11 | Provider/AITool/Model separados; credentials por referência; completion síncrona. |
| Observability | ADR-13 / ADR-26 | AIRequestCompleted como origem operacional; histórico separado. |
| Governance Score | ADR-20 / ADR-21 / ADR-27 | Score determinístico, missing data explícito e snapshots mensais. |
| Persistência | ADR-29 | PostgreSQL único, ownership por módulo, sem FKs cross-module; execução local ou Docker Compose sem alterar o C4. |
| Estrutura de código | ADR-31 | Maven único + packages modulares api/domain/application/infrastructure. |

## 8. Conclusão

A arquitetura da Plataforma QSOFT permanece um Monólito Modular Java/Spring Boot. O C4 Model é utilizado exclusivamente como forma de documentação e demonstração: C1 explica o ecossistema, C2 mostra as unidades executáveis e de persistência, C3 evidencia os módulos internos e suas relações, e o exemplo C4-Code demonstra como uma dessas fronteiras pode ser refletida na estrutura Java.

| Resultado: A adoção desta documentação C4 não exige remodelagem do sistema. Ela organiza visualmente decisões já existentes e torna a arquitetura mais fácil de apresentar, revisar e relacionar com os ADRs. |
| --- |

Fonte e versão: Arquitetura Funcional da Plataforma de Governança de IA — QSOFT, baseline consolidada de agosto de 2026, alinhada aos 31 ADRs formais. Em caso de divergência com cópia anterior, prevalece a versão final consolidada.