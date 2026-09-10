---
name: testing
description: Seleciona testes proporcionais ao risco dentro da estratégia QSOFT.
---

# Testing

Leia `ai/testing.md`.

Prioridade:

1. unit para regra/invariante;
2. ArchUnit para fronteira;
3. integration PostgreSQL para persistência/tenant;
4. security/API para autorização;
5. E2E somente quando necessário.

`mvn test` não exige Docker.
`mvn verify -Pintegration` exige Docker/Testcontainers.
