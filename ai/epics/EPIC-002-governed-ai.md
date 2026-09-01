# EPIC-002 — Gateway de IA, Chat e Agent Policies
## Objetivo
Oferecer uso governado de IA e políticas internas sem expor provider nem conteúdo privado.
## Módulos
`aigateway`, `chat`, `agentpolicies`
## Critérios arquiteturais
- routing determinístico;
- autorização antes do LLM;
- Chat privado;
- Provider Adapter em infrastructure;
- AIRequestCompleted como uso operacional.
