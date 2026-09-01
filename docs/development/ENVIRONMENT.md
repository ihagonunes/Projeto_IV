# Ambiente de desenvolvimento

## Requisitos mínimos

- JDK 21
- Maven 3.9+
- PostgreSQL 18 recomendado para ambiente local
- Git
- Docker Desktop/Engine apenas se usar modo Docker ou Testcontainers

## Modo local

Mais leve para máquinas com menos recursos:

```text
IDE/Maven → Spring Boot
              ↓
       PostgreSQL local
```

Use `scripts/run-local.ps1` no Windows ou `scripts/run-local.sh`.

## Modo Docker

```text
Docker Compose
├── backend
└── PostgreSQL 18.6
```

Use para ambiente reproduzível, integração, CI e demonstração.

## IDE

### VS Code

Abra o workspace e instale as extensões recomendadas em `.vscode/extensions.json`.

### IntelliJ IDEA

Recursos recomendados:
- Java 21 SDK;
- Maven import automático;
- Spring/Spring Boot support quando disponível na edição usada;
- Docker plugin apenas se necessário;
- Database/PostgreSQL tooling opcional.

Não é necessário instalar plugin de Lombok porque Lombok não faz parte da baseline.
