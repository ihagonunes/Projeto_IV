# Bootstrap técnico

## Primeira execução

1. Configure PostgreSQL.
2. Copie `.env.example` → `.env`.
3. Execute `mvn test`.
4. Execute a aplicação.
5. Confirme `/actuator/health`.

## Primeira feature recomendada

Começar por IAM:

1. Tenant;
2. User;
3. login `tenantSlug + email + password`;
4. JWT;
5. TenantContext;
6. AuthorizationScope.

Antes de criar tabelas, adicione migration Flyway.

## Segurança inicial

O starter usa `denyAll` para endpoints de negócio até IAM/JWT ser implementado.
Isso é intencional: nenhuma API nova deve ficar pública por acidente.
