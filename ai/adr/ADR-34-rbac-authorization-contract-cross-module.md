# ADR-34 — Contrato de autorização RBAC exposto a outros módulos

> ⚠️ **Emendado pelo ADR-36 (ACCEPTED, 2026-10-18).** `@RequiresPermission` ganha `targeting` (`TENANT_ONLY` por padrão; `BY_TARGET` exige checagem fina no caso de uso). Novos tipos `ScopeType`, `AccessTarget` e `VisibilityScope` entram no `sharedkernel.rbac`. A emenda formal entra pela TASK-S3-A10.

## Status

ACCEPTED (aprovado — implementado em conjunto com o módulo `department`)

## Contexto

O ADR-04 define que o IAM é dono de `Permissions` e `AuthorizationScope`; o
ADR-31 define que **outro módulo importa somente `module.api.*`**, e o ArchUnit
aplica essa allowlist (para `department`, só `iam.api.*` é acessível).

Porém, hoje o vocabulário e a anotação de autorização vivem em pacotes
**internos** do IAM:

- `Resource` → `iam.domain.rbac.Resource`
- `Action` → `iam.domain.rbac.Action`
- `RequiresPermission` → `iam.infrastructure.web.RequiresPermission`

Consequência: **nenhum outro módulo consegue declarar a permissão exigida** por
seus endpoints usando o mecanismo oficial (`@RequiresPermission` +
`PermissionInterceptor`). O módulo `department` (pré-requisito do POPROJ-21)
esbarraria nisso: seus endpoints ficariam apenas **autenticados**, sem
autorização — contrariando o `AGENTS.md` ("operações sensíveis devem ser
autorizadas pelo backend").

## Decisão

Expor o **contrato de autorização** para os demais módulos, mantendo o IAM como
dono do modelo:

1. `Resource` e `Action` passam para **`sharedkernel`** (`sharedkernel/rbac/`),
   por serem **vocabulário transversal** — cada módulo declara quais recursos e
   ações protege.
2. `RequiresPermission` passa para **`iam/api/rbac/`** (superfície pública do
   IAM), referenciando o vocabulário do sharedkernel.
3. `iam.domain.rbac.Permission` (record `Resource, Action`) continua no domínio
   do IAM e passa a referenciar o vocabulário do sharedkernel.
4. `PolicyEvaluator`, `PermissionInterceptor`, `PermissionMatrix` e demais
   componentes internos permanecem no IAM — apenas trocam o import.

O **modelo de autorização** (papéis por tenant, avaliação de política, escopo de
department) **continua integralmente no IAM** (ADR-04/ADR-33). O que se torna
transversal é somente o **vocabulário** usado para declarar a permissão exigida.

## Alternativas consideradas

- **Manter tudo em `iam.domain.rbac`/`iam.infrastructure.web`**: mantém a
  coesão, mas inviabiliza RBAC em qualquer outro módulo sem violar o ArchUnit.
- **Mover o vocabulário e a anotação para `iam.api.rbac`**: respeita o ADR-04
  literalmente, mas cria dependência `iam.domain → iam.api` (inversão de
  camadas) e risco futuro de ciclo entre `api` e `domain`.
- **Cada módulo com enum próprio + resolução por string**: duplica vocabulário e
  enfraquece a verificação em tempo de compilação.
- **Duplicar o interceptor por módulo**: multiplica código de segurança — pior
  das opções.

## Emenda ao ADR-04

O ADR-04 define que o IAM é dono de `Permissions`. Esta decisão **não altera esse
ownership**: o **modelo** de autorização (papéis por tenant, `PolicyEvaluator`,
escopo por department, provisionamento de papéis padrão) permanece
integralmente no IAM (ADR-04/ADR-33).

O que passa a ser **transversal** é apenas o **vocabulário** `resource × action`
(`Resource`/`Action` no `sharedkernel`), porque **todo módulo precisa declarar
quais recursos protege**. O ADR-04 fica assim **esclarecido, não revogado**.

## Emenda (ADR-36/ADR-37) — 2026-10-18

`@RequiresPermission` passa a declarar **targeting**:

```java
Targeting targeting() default Targeting.TENANT_ONLY;
```

- `TENANT_ONLY` (padrão, gate fechado): exige uma concessão aplicável cujo teto efetivo
  seja `TENANT`.
- `BY_TARGET`: aceita qualquer concessão aplicável e **obriga** o caso de uso a fazer a
  checagem fina com `AuthorizationQuery.isAllowed(..., AccessTarget)` (ou
  `visibilityFor` para consultas agregadas). Sem essa checagem, o endpoint não deve ser
  publicado.
- Entram no `sharedkernel.rbac`, além de `Resource`/`Action`: `ScopeType`, `AccessTarget`
  (`TenantTarget`, `DepartmentTarget`, `OwnedTarget`), `VisibilityScope` (`TenantWide`,
  `Departments`, `SelfOnly`, `None`) e `Targeting`.
- O teto de `CHAT` é sempre `SELF`, independentemente do escopo da concessão (ADR-36,
  item 5), e as concessões são lidas a cada requisição, sem cache.

O Status continua `ACCEPTED`.

## Consequências

- Positivo: habilita RBAC cross-module **sem violar a allowlist** do ADR-31; o
  vocabulário permanece único (verificação em tempo de compilação).
- Positivo: `sharedkernel` ganha apenas enums sem dependências — segue
  respeitando "não depende de módulos de negócio".
- Negativo: `sharedkernel` deixa de conter somente IDs/contexto e passa a
  incluir o vocabulário de autorização.
- Restrição: o **modelo** de autorização continua no IAM; nenhum módulo pode
  reimplementar avaliação de permissão.
- Restrição: mudança mecânica de imports em `iam` (sem alteração de
  comportamento), coberta pelos testes de autorização existentes.
