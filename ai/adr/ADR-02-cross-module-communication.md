# ADR-02 — Cross-module Communication

## Status

ACCEPTED

## Contexto

Módulos precisam colaborar sem compartilhar entidades, repositories ou services internos.

## Decisão

Comunicação cross-module ocorre somente por Command Port, Query Port ou Domain Event expostos em module.api.*. Dependências seguem allowlist explícita e ciclos são proibidos.

## Alternativas consideradas

Imports diretos; banco compartilhado como integração; eventos para qualquer consulta síncrona.

## Consequências

Contratos ficam rastreáveis e testáveis. Mudanças internas permanecem encapsuladas, ao custo de DTOs/ports explícitos.
