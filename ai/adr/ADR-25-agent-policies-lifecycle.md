# ADR-25 — Agent Policies Lifecycle

## Status

ACCEPTED

## Contexto

Versões de documentos e execuções do agente precisam de estados mínimos para rastreabilidade e consistência.

## Decisão

DocumentVersion: DRAFT→PENDING_APPROVAL→ACTIVE→SUPERSEDED, com no máximo uma ACTIVE por documento. AgentExecution: RUNNING→COMPLETED|FAILED. AgentExecutionCompleted só é criado se houver consumidor futuro explícito.

## Alternativas consideradas

Documento sem versionamento; múltiplas versões ativas; evento obrigatório sem consumidor.

## Consequências

Ciclo fica auditável e evita ambiguidade de conhecimento vigente; aprovação precisa ser atômica.
