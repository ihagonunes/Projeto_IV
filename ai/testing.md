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

### Agentes (planejado — ADR-44/45)

- **Unit:** validação do DAG, topological sort, fan-out/merge `CONCATENATE`,
  fail-fast, timeout, idempotência e serialização versionada do grafo.
- **ArchUnit:** `agents` só acessa `sharedkernel`, `iam.api`, `aigateway.api` e
  `agentpolicies.api`; nenhum import interno cross-module.
- **Integration PostgreSQL/Testcontainers:** catálogo global + override por
  tenant, versões imutáveis, concorrência otimista, tenant A ≠ B, RBAC 403/404,
  execução de fluxo linear e com um ramo/merge, recibos por etapa e limites de
  payload.
- **Provider simulado:** usar `STUB`/servidor HTTP local do JDK; nenhum provider
  externo obrigatório no CI.
- **Frontend desktop:** editor, foco por teclado, salvar/carregar, conflito e
  mensagens de validação. Mobile/touch não é requisito do produto.
- **E2E de navegador:** complementar, para drag-and-drop desktop e diálogo; não
  substitui unit/ArchUnit/integração.

### E2E

Selenium é complementar e só entra quando houver frontend formalizado. Não substitui testes de arquitetura, integração ou segurança.
