# Estratégia de testes

## Ciclo rápido

```bash
mvn test
```

Executa testes unitários e ArchUnit. Não exige Docker.

## Integração

```bash
mvn verify -Pintegration
```

Executa `*IT` com Testcontainers/PostgreSQL real e exige Docker.

## Camadas

### Architecture

- allowlist de módulos;
- cross-module somente `module.api.*`;
- ausência de ciclos;
- domain sem Spring/JPA/application/infrastructure;
- sharedkernel sem dependência de negócio.

### Unit

- invariantes de aggregate/value object;
- cálculo de score/créditos;
- routing determinístico;
- lifecycle de documentos/execuções.

### Integration

- PostgreSQL + Flyway;
- Tenant A não lê Tenant B;
- idempotência por RequestId;
- constraints e índices;
- adapters históricos;
- repository por módulo.

### Security/API

- login com `email + password`; `tenantSlug` opcional, com tenant resolvido pelo
  domínio de e-mail cadastrado (ADR-32, emendado pela SPI-54);
- Tenant/User INACTIVE;
- ownership de Chat;
- concessões com escopo (`AuthorizationQuery.isAllowed`/`visibilityFor`) e `targeting`;
- revogação de concessão vale na requisição seguinte (sem cache);
- paginação;
- endpoint privado/publicado corretamente.

### E2E

Selenium é complementar e só entra quando houver frontend formalizado. Não substitui testes de arquitetura, integração ou segurança.
