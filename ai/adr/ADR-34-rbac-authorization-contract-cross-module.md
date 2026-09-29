# ADR-34 — Contrato de autorização RBAC exposto a outros módulos

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
