# ADR-36 — Concessão com escopo como fonte única de autorização

## Status

ACCEPTED (2026-10-18).

## Contexto

A verificação da Fase 0 (`docs/review/VERIFICACAO_FASE0.md`) confirmou três problemas no modelo atual:

- **Duas trilhas de autorização.** O gate HTTP usa papéis (`iam_role_assignments`, ADR-33). Mas a
  visibilidade do dashboard vem de `AuthorizationQuery.resolveScope`, que lê o enum legado
  `user_permission` (V004, via `User.isAdmin()`) e o rótulo editável `hierarchyLevel`.
- **Escopo derivado do usuário, não da concessão.** O escopo DEPARTMENT usa o `departmentId` atual
  do usuário, que um MANAGER pode alterar em si mesmo. Se o departamento for nulo, a visibilidade
  passa a ser do tenant inteiro (*fail-open*).
- **Atribuição departamental inerte.** O interceptor avalia sem departamento-alvo. Por isso, uma
  atribuição com `department_id` nunca se aplica (*fail-closed*).

Além disso, `GET /api/v1/observability/usage` não aplica escopo algum.

## Decisão

1. **Fonte única.** A única fonte de autorização é `iam_role_assignments`. Uma **concessão** é
   (tenant, usuário, papel de sistema, escopo). O escopo pertence à concessão. Nunca vem do rótulo
   nem do departamento atual do usuário.
2. **Papéis de sistema com escopo fixo** (MVP, sem papéis customizados):

   | Papel | Escopo | Posto |
   |---|---|---|
   | COLLABORATOR | SELF | 1 |
   | LEADER | DEPARTMENT | 2 |
   | MANAGER | DEPARTMENT | 3 |
   | EXECUTIVE | TENANT | 4 |
   | TENANT_ADMIN | TENANT | administrativo, fora da escada |

   Uma concessão DEPARTMENT guarda o `department_id` da concessão. Ele é copiado na criação e não
   acompanha mudanças posteriores de lotação do usuário.
3. **Alvo.** Toda decisão fina recebe um `AccessTarget` (`sharedkernel.rbac`): `TenantTarget`,
   `DepartmentTarget(d)` ou `OwnedTarget(owner, ownerDept)`. O módulo dono do recurso resolve o
   alvo. Regras de cobertura:
   - TENANT cobre qualquer alvo do tenant;
   - DEPARTMENT(d) cobre `DepartmentTarget(d)` e `OwnedTarget(_, d)`;
   - SELF cobre `OwnedTarget(ator, _)`.
4. **Visibilidade.** Para consultas agregadas, o IAM devolve um `VisibilityScope` (`TenantWide`,
   `Departments(ids, self)`, `SelfOnly(self)` ou `None`), que é a união das concessões ativas que
   contêm a permissão. Não existe caminho nulo-permissivo: sem concessão aplicável, o resultado é
   `None`.
5. **Teto por recurso.** `CHAT:*` tem escopo efetivo SELF, qualquer que seja o escopo da concessão
   (reforça o ADR-16).
6. **`MANAGE` não implica outras ações.** O *match* continua exato (comportamento atual).
7. **Gate fechado por padrão.** `@RequiresPermission` ganha `targeting`:
   - `TENANT_ONLY` (padrão) exige concessão de escopo TENANT;
   - `BY_TARGET` aceita qualquer escopo no gate e obriga o caso de uso a chamar `isAllowed` ou
     `visibilityFor`.
8. **Leitura a cada requisição.** As concessões são lidas do banco em toda requisição. Nada de
   autorização nos claims do JWT (que já são estáveis — ADR-30 sem alteração).
9. **Rótulo sem efeito.** `hierarchyLevel` vira rótulo organizacional. Nenhuma classe de autorização
   o lê; uma regra ArchUnit garante isso.
10. **Convenção de resposta.** Recurso de outro tenant → 404. Recurso do mesmo tenant fora do
    escopo, ou filtro pedido fora do escopo → 403.
11. **Departamento não validado pelo IAM.** O `department_id` da concessão não é validado pelo
    `iam`: a allowlist proíbe `iam → department`. Uma concessão para departamento inexistente ou de
    outro tenant não casa com nenhum alvo (*fail-closed*).

## Alternativas consideradas

- *Fallback* para a trilha legada: reabre a divergência entre as trilhas de forma permissiva.
- Preencher o departamento do contexto com o departamento do ator: confunde ator com alvo.
- Escopo por permissão dentro do papel: dobra o schema e os testes, sem caso de uso atual.
- `MANAGE` implicando as demais ações: amplia o acesso atual sem demanda.
- Validar o departamento via `OrganizationQuery`: cria o ciclo `iam ↔ department`.

## Consequências

- `AuthorizationQuery` muda de forma incompatível: saem `checkPermission`, `resolveScope` e o enum
  `iam.api.model.Permission`; entram `isAllowed` e `visibilityFor`. Os consumidores são todos
  internos ao monólito.
- Endpoints de recurso escopado precisam resolver o alvo no caso de uso. Também passam a declarar
  `BY_TARGET`: dashboard, consulta de uso e usuários.
- Emendas: ADR-04 (admin passa a ser o papel `TENANT_ADMIN`), ADR-33 (escopo explícito na concessão)
  e ADR-34 (`targeting`).
- A V015 expande o schema e faz o backfill. Ela aborta em caso ambíguo. O `DROP` de
  `user_permission` fica para uma migration do release seguinte (V021+; V016–V019 estão reservadas na Sprint 3).

## Emenda 23/10/2026 — administração separada dos dashboards (FEAT-DASH-001 local)

Vulpi definiu duas experiências: EXECUTIVE consulta dashboards e gera convites;
TENANT_ADMIN cadastra empresas, gera convites e edita identidade visual, sem
página de dashboard. Não criar tabela de autenticação ou login paralelo: ambos
usam app_user e concessões atuais; hierarchyLevel continua sem autorizar acesso.

Retirar USAGE:READ e GOVERNANCE_SCORE:READ do papel TENANT_ADMIN no catálogo de
domínio e nos papéis persistidos pela V021. Permanecem 18 permissões técnicas e
administrativas. Não retirar concessões ou permissões dos demais papéis, nem
transformar EXECUTIVE em administrador. A autorização por concessões continua
aditiva: permissões analíticas exigem papel analítico explícito.

O BFF mostra no onboarding os botões do perfil administrativo e redireciona
qualquer /dashboard desse perfil para /onboarding, inclusive perfis na URL.
As APIs de métricas retornam 403 para conta exclusivamente TENANT_ADMIN.
Cadastro e branding são páginas administrativas; o backend mantém TENANT:MANAGE
para edição da empresa e identidade visual. O cadastro inicial público da API
mantém seu contrato existente de bootstrap de novo tenant.

Alternativa descartada: esconder só o botão, que deixaria as APIs analíticas
acessíveis. Consequência: testes analíticos usam EXECUTIVE; testes de administração
usam TENANT_ADMIN. A V015 permanece imutável; V021 atualiza bases existentes.
