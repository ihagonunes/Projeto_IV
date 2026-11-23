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

### Agentes (ADR-44/45)

- **Implementado (V027/V028 + BE-02..BE-06):** unit do modelo de domínio,
  `SystemRole` com `AGENT:*`, persistência/isolamento por tenant, seed global
  (V029), vocabulário fechado das políticas, API do catálogo, CRUD de fluxos
  com versão imutável, validação do DAG/publicação e execução síncrona com
  etapas/recibos (idempotência, fail-fast, timeout, `CONCATENATE`, Tenant A ≠ B).
- **ArchUnit:** `agents` só acessa `sharedkernel`, `iam.api`, `aigateway.api` e
  `agentpolicies.api`; nenhum import interno cross-module; `chat -> agents` proibido
  no MVP.
- **Planejado:** validação do DAG, topological sort, fan-out/merge `CONCATENATE`,
  fail-fast, timeout, idempotência e serialização versionada do grafo.
- **Integration PostgreSQL/Testcontainers (fundação):** catálogo global +
  override por tenant, versões por fluxo, tenant A ≠ B, constraints de status e
  unicidade, migração RBAC idempotente e decisões `AGENT:*` (403/404).
- **Integration planejado:** concorrência otimista, execução de fluxo linear e
  com um ramo/merge, recibos por etapa e limites de payload.
- **Provider simulado:** usar `STUB`/servidor HTTP local do JDK; nenhum provider
  externo obrigatório no CI.
- **Frontend desktop:** editor, foco por teclado, salvar/carregar, conflito e
  mensagens de validação. Mobile/touch não é requisito do produto.
- **Implementado (BE-05):** catálogo via API, `If-Match` na persistência,
  conflito 409/412, erros por nó, foco preservado após render, DRAFT não
  adicionável e prévia isolada (sem client/fetch).
- **E2E de navegador:** complementar, para drag-and-drop desktop e diálogo; não
  substitui unit/ArchUnit/integração.
- **E2E de agentes (BE-07):** executado em 22/11/2026 com Playwright MCP sobre
  backend 8081 + front 3100 + PostgreSQL descartável + provider STUB + seed via
  API. Fluxo de 5 agentes publicado e executado na UI: `SUCCEEDED`, 9 etapas,
  5 recibos `KNOWN`, custo `0.00828`. Em 23/11/2026 foram cobertos também:
  drag real (`dragTo`), conflito 412 live no save, isolamento live (Tenant B →
  404), 412 de publicação (DOM) e concorrência real
  (`AgentsFlowConcurrencyIT`). Runbook e evidências em
  `docs/development/E2E_AGENTES_PLAYWRIGHT.md`. Nenhuma dependência npm nova.

### E2E

Selenium é complementar e só entra quando houver frontend formalizado. Não substitui testes de arquitetura, integração ou segurança.
