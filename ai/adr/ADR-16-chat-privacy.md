# ADR-16 — Chat Privacy

## Status

ACCEPTED

## Contexto

Conteúdo conversacional pode conter informação sensível e não é necessário para analytics gerenciais.

## Decisão

Somente o proprietário pode visualizar, enviar, arquivar ou excluir Conversation/Message. MANAGER/ADMIN não obtêm acesso por hierarquia. Observability recebe somente metadados.

## Alternativas consideradas

Administrador com acesso universal; conteúdo copiado para analytics.

## Consequências

Fortalece privacidade, mas suporte/auditoria precisa operar sem leitura indiscriminada do conteúdo.
