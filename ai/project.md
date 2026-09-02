# Projeto

## Nome

Plataforma QSOFT de Governança Corporativa de IA

## Problema

Empresas precisam reduzir Shadow AI e governar uso corporativo de modelos/ferramentas de IA sem transformar governança em bloqueio indiscriminado.

## Objetivo

Centralizar acesso governado à IA, políticas internas, observabilidade de consumo/custo, engajamento/capacitação e indicadores executivos.

## Cliente/contexto

Projeto Integrador IV em parceria com a QSOFT. Produto white-label e multi-tenant.

## Escopo MVP

### Incluído

- Tenant/User/IAM e autorização determinística.
- Department/Area e escopos organizacionais.
- Project mínimo como dimensão analítica.
- Catálogo Provider/AITool/Model e ModelPolicy por Department.
- Chat corporativo privado.
- Agente corporativo de políticas/conhecimento interno.
- Observability de uso, custo, orçamento e alertas.
- Engagement, AILicense, TrainingRecord e AI Credits.
- AI Adoption & Governance Score.
- PostgreSQL e histórico analítico separado.

### Fora de escopo

- microservices;
- broker/event bus distribuído;
- fallback automático de modelo;
- roteamento por custo/latência;
- multiagentes;
- RAG obrigatório;
- pgvector obrigatório;
- refresh token;
- benchmarking entre tenants;
- gestão completa de projetos/sprints/issues;
- H2 como banco suportado.

## Princípios

- autorização antes de IA;
- indicadores não bloqueiam acesso;
- conteúdo privado não alimenta analytics;
- um deploy, um PostgreSQL, módulos lógicos fortes.
