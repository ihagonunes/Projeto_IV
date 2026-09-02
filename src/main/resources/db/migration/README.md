# Flyway migrations

PostgreSQL é o único banco oficial.

Convenção:

```text
V001__create_iam_tables.sql
V002__create_department_tables.sql
V003__create_ai_gateway_tables.sql
...
```

Regras:

- migration acompanha toda mudança de persistência;
- `spring.jpa.hibernate.ddl-auto=validate`;
- FK somente dentro do mesmo módulo;
- relações cross-module persistem IDs, sem FK SQL;
- tabela de negócio consultada independentemente deve ser tenant-aware;
- SQL nativo tenant-aware precisa filtrar `tenant_id`;
- dados históricos devem usar tabelas `historical_*`.
