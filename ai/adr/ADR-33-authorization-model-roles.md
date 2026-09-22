# ADR-33 — Modelo de autorização (RBAC por papéis por tenant)

## Status

ACCEPTED

## Contexto

A SPI-39 (modelo de dados de permissões) foi implementada **duas vezes em paralelo**,
em branches diferentes, e o merge trouxe as duas implementações para a mesma base:

1. **Papéis por tenant** (`iam/domain/rbac`): tabelas `iam_roles`,
   `iam_role_permissions` (`resource` × `action`) e `iam_role_assignments`
   (papel → usuário, com `department_id` opcional). O papel é **por tenant**.
2. **Matriz global** (`iam/domain/permission`): tabela `permission_grant`
   (`role_level`, `resource`, `action`), **sem `tenant_id`** — a mesma matriz
   para todos os tenants, indexada pelo `HierarchyLevel` do usuário.

A coexistência gerou:
- duas migrations com a **mesma versão** (`V005`), quebrando o Flyway (`Found more
  than one migration with version 005`) e impedindo a aplicação de iniciar;
- dois enums `Resource`/`Action` e duas entidades de domínio concorrentes;
- risco de duas fontes de verdade para autorização.

Avaliação de uso: o modelo de **papéis** era o único conectado ao runtime
(`PolicyEvaluator`, `PermissionInterceptor`, `UserController`, `DemoDataSeeder`,
`ProvisionDefaultRoles`). O modelo `permission_grant` estava **órfão** — sem
consumidor fora do próprio pacote e dos seus testes.

## Decisão

Adotar o modelo de **papéis por tenant** (`iam/domain/rbac`) como única fonte de
verdade de autorização e **remover** o modelo de matriz global
(`permission_grant`), incluindo migration, pacote de domínio, persistência e testes.

A autorização permanece: `PolicyEvaluator` resolve as permissões efetivas do
usuário a partir das atribuições de papel (`iam_role_assignments`), com escopo
opcional de Department avaliado em tempo de execução (não persistido na tupla).

## Alternativas consideradas

- **Adotar a matriz global `permission_grant`**: descartada — ignora o isolamento
  por tenant (ADR-03) e não permitiria customização de papéis por organização.
- **Manter os dois modelos**: descartada — duas fontes de verdade, migrations
  duplicadas e risco de decisões de autorização divergentes.
- **Renumerar a migration duplicada e manter o código órfão**: descartada — não
  resolve a duplicidade de modelos, apenas adia o conflito.

## Consequências

- Positivo: uma única fonte de verdade; papéis isolados por `tenant_id`
  (ADR-03/ADR-04) e customizáveis por organização; escopo de Department
  representável por atribuição.
- Positivo: Flyway volta a ter versões únicas (`V001`–`V006`).
- Negativo: modelo com mais tabelas/joins do que a matriz global.
- Restrição: toda autorização deve passar por `PolicyEvaluator`/`PermissionInterceptor`;
  não introduzir novamente uma matriz global paralela.
- Restrição: o trabalho da variante `permission_grant` é descartado; quem depender
  dele deve migrar para o modelo de papéis.
