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

Ferramentas de carga histórica (BUGFIX-DB-001): Node.js 24, já usado pelo BFF,
e cliente `psql` do PostgreSQL. O importador usa somente módulos nativos do Node,
sem novas bibliotecas npm/Maven. O teste `HistoricalLoaderIT` executa o CLI Node
real contra PostgreSQL/Testcontainers e requer `node` e `psql` no PATH. A CI
prepara explicitamente essas ferramentas; a execução local não exige Docker,
exceto para a suíte de integração.

| Biblioteca | Uso |
|---|---|
| JUnit 5 / Spring Boot Test | unit/application/API tests |
| Spring Security Test | authorization/security |
| ArchUnit 1.4.2 | enforcement modular |
| Testcontainers 2.0.5 (`testcontainers-postgresql` + `testcontainers-junit-jupiter`) | integração com PostgreSQL real |
| JaCoCo 0.8.15 | cobertura de código (relatório em target/site/jacoco) |

## Frontend (npm)

O BFF/EJS usa somente as dependências já declaradas em `package.json`: Express,
EJS, `express-ejs-layouts`, `express-async-error-wrapper`, `cookie-parser`,
`compression`, `dotenv`, `axios`, `lru-cache` e `multer`. Não há bundler, framework
SPA, biblioteca de componentes ou cliente HTTP adicional no browser: o front usa
`fetch` e o `ApiClient` existente.

Os testes do front usam o executor nativo `node --test` e `fetch` do Node; não há
dependência npm de teste. Na Sprint 4 não entrou nenhuma dependência Maven nem npm
nova. O servidor HTTP de teste dos providers usa `com.sun.net.httpserver` do JDK;
a projeção de orçamento e o engajamento são cálculo puro no backend.

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
