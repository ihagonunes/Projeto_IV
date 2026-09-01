# ADR-10 — Provider Credentials

## Status

ACCEPTED

## Contexto

Credenciais BYOK são sensíveis e não podem circular pelo domínio ou observabilidade.

## Decisão

Domínio persiste apenas SecretReference/CredentialReference. CredentialProvider resolve o segredo via Secret Store na infraestrutura. Segredos não aparecem em logs, eventos ou DTOs públicos.

## Alternativas consideradas

Chave em tabela em texto puro; chave em application.yml; chave enviada ao frontend.

## Consequências

Reduz superfície de exposição e permite trocar tecnologia de Secret Store sem alterar domínio.
