# Catálogo de dependências

## Runtime

| Tecnologia | Uso | Política |
|---|---|---|
| Java 21 | runtime/LTS | obrigatório |
| Spring Boot 4.1.1 | bootstrap/dependency management | baseline |
| Spring Web | REST | obrigatório |
| Spring `RestClient` + `JdkClientHttpRequestFactory` | chamada HTTP ao provider de IA | **provider via HTTP (`RestClient`), sem SDK** |
| Spring Data JPA | persistência | obrigatório |
| PostgreSQL | SGBD | único banco suportado |
| Flyway | migrations | obrigatório |
| Spring Validation | DTO/input validation | obrigatório |
| Spring Security | autenticação/autorização | obrigatório |
| OAuth2 Resource Server | primitives JWT Bearer | usado quando IAM/JWT for implementado |
| Actuator | health/info | obrigatório para operação |

## Teste

| Biblioteca | Uso |
|---|---|
| JUnit 5 / Spring Boot Test | unit/application/API tests |
| Spring Security Test | authorization/security |
| ArchUnit 1.4.2 | enforcement modular |
| Testcontainers 2.0.5 (`testcontainers-postgresql` + `testcontainers-junit-jupiter`) | integração com PostgreSQL real |
| JaCoCo 0.8.15 | cobertura de código (relatório em target/site/jacoco) |


## Não adicionar agora

- H2;
- Lombok;
- MapStruct;
- Spring Modulith;
- Spring AI;
- smolagents/Python runtime;
- Redis;
- Kafka/RabbitMQ;
- pgvector;
- Elasticsearch;
- OpenTelemetry backend/vendor SDK;
- SDK concreto de provider de IA.

Esses itens exigem necessidade funcional explícita. Framework de provider e KnowledgeAccess permanecem abstratos.

O provider de IA é acessado via HTTP (`RestClient`) a partir da porta `ProviderClient` (ADR-38),
**sem SDK** concreto e sem Spring AI.

## Política

Toda dependência nova deve responder:

1. qual problema real resolve;
2. por que a JDK/Spring atual não basta;
3. qual módulo é proprietário;
4. qual custo de manutenção/teste;
5. se altera propriedade arquitetural → ADR.


## Frontend de demonstração dos dashboards

Chart.js 4.4.8 via CDN fixada: gráficos combinados e barras horizontais empilhadas no módulo frontend dashboard. Bootstrap existente não fornece gráficos. Dispensa framework adicional e mudanças ao backend. Custo: acompanhar atualizações da CDN e validar responsividade; tabelas acessíveis permanecem disponíveis se o script falhar. Referência: https://www.chartjs.org/docs/latest/charts/mixed.html e docs/development/DASHBOARDS_DEMO.md.
