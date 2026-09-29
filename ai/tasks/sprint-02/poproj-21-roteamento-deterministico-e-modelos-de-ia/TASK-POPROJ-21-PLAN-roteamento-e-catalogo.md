# TASK-POPROJ-21-PLAN — Roteamento determinístico e catálogo de modelos

## Metadados
- **Projeto:** SPI · **Sprint:** SPI Sprint 2
- **Chave:** `POPROJ-21`
- **História PO:** POPROJ-21 — Roteamento Determinístico e Modelos de IA
- **Tasks cobertas:** SPI-79, SPI-80, SPI-81, SPI-82, SPI-83, SPI-84, SPI-85, SPI-86
- **Status:** Proposta (aguardando aprovação das 2 decisões antes de codar)

## Contexto

O bloco divide-se em **dois módulos**, conforme os ADRs:

| Dono | O que possui | ADR |
|---|---|---|
| `aigateway` | `Provider`, `AITool`, `Model` (estado `AVAILABLE`/`DISABLED`, referência por `providerId`) | ADR-09 |
| `department` | **`ModelPolicy`** (`allowedModelIds[]` + `defaultModelId`) | ADR-07 / ADR-08 |

Regra de routing (SPI-83, ADR-07/08/11) — **determinística, sem heurística/custo/latência**:

```text
requestedModelId presente → precisa pertencer a allowedModelIds
requestedModelId ausente  → usa defaultModelId
sempre: Model AVAILABLE e Provider ACTIVE
```

## 🔴 Decisão 1 — Onde orquestrar a validação dos ModelIds (SPI-82)

A task diz: *"Validar ModelIds por API pública do Gateway"*. O ADR-07 diz: *"ai-gateway valida IDs no catálogo, consulta e aplica allowedModelIds/defaultModelId. **Administração orquestra validação sem criar ciclo Department→Gateway**"*.

A allowlist **impede** `department → aigateway`. Portanto a validação **não pode** ficar no módulo `department`.

**Proposta (recomendada):**
- `department.api` expõe um **contrato de comando** (ex.: `ConfigureDepartmentModelPolicy`) que persiste `allowedModelIds`/`defaultModelId` — o `department` continua **dono do dado**.
- O **`aigateway` orquestra**: expõe o endpoint administrativo (`/api/v1/ai-gateway/departments/{id}/model-policy`), **valida os ModelIds no seu próprio catálogo** e chama o comando do `department`.
- Direção de dependência: `aigateway → department` ✓ (já permitida) — **sem ciclo**.

**Alternativa:** um serviço de orquestração em `platform` (pacote neutro, fora da allowlist de módulos de negócio) chamando os dois. Menos coeso (o `platform` é descrito como bootstrap/config técnica).

## 🔴 Decisão 2 — Allowlist: `aigateway → iam`

SPI-84 diz explicitamente *"IAM decide autorização"*. Porém a allowlist atual é `aigateway → {department}` — **o único módulo sem `iam`**. Com o ADR-34, qualquer endpoint protegido usa `iam.api.rbac.@RequiresPermission`, então o `aigateway` precisa de `iam` na allowlist.

**Proposta (recomendada):** adicionar `iam` à allowlist de `aigateway` em `ai/architecture.md` + `ArchitectureRulesTest`. É uma **correção de omissão** (todos os outros módulos já têm `iam`), coerente com ADR-34 e SPI-84.

> Se não aprovada, os endpoints do gateway ficariam **sem autorização** — mesma situação que o ADR-34 resolveu para o `department`.

## Modelo de dados

**`V010` (aigateway)** — catálogo técnico:
```sql
aigateway_providers (id, tenant_id, name, status[ACTIVE|INACTIVE], created_at, updated_at,
                     UNIQUE(tenant_id, name))
aigateway_models    (id, tenant_id, provider_id, name, status[AVAILABLE|DISABLED],
                     created_at, updated_at, UNIQUE(tenant_id, name))
```
`provider_id` é **intramódulo** (mesmo módulo) → FK permitida ✓. `tenant_id` **sem FK cross-module** (ADR-29).

**`V011` (department)** — política organizacional:
```sql
department_model_policies (department_id PK, allowed_model_ids UUID[], default_model_id UUID,
                           created_at, updated_at)
```
`allowed_model_ids`/`default_model_id` são **referências cross-module por ID**, sem FK (ADR-29/07). Invariante (ADR-08): se `allowed_model_ids` não vazio, `default_model_id` é obrigatório **e pertence ao conjunto**; conjunto vazio ⇒ default nulo.

## API

| Método | Caminho | Task | Autorização |
|---|---|---|---|
| GET/POST | `/api/v1/ai-gateway/providers` | SPI-81 | `AI_TOOL:MANAGE` |
| GET/POST | `/api/v1/ai-gateway/models` | SPI-81 | `AI_TOOL:MANAGE` |
| PATCH | `/api/v1/ai-gateway/models/{id}/status` | SPI-81 | `AI_TOOL:MANAGE` |
| PUT | `/api/v1/ai-gateway/departments/{id}/model-policy` | SPI-82 | `AI_TOOL:MANAGE` |
| GET | `/api/v1/ai-gateway/models/permitido` | SPI-84 | autenticado (`AI_TOOL:READ`?) |

## Contratos públicos

- `aigateway.api.query.AIToolCatalogQuery` — validação de ModelIds e leitura do catálogo (consumido pelo próprio gateway/admin e por módulos futuros).
- `department.api.query.DepartmentModelPolicyQuery` — o gateway lê `allowedModelIds`/`defaultModelId` (já previsto em `ai/contracts.md`).
- `department.api.command.ConfigureDepartmentModelPolicy` — comando usado pela orquestração do gateway.

## Erros (SPI-85)

- `ModelNotAllowedException` — `requestedModelId` fora de `allowedModelIds`.
- `NoModelConfiguredException` — departamento sem política/default; mensagem orienta contatar o administrador, **sem** modelo aleatório/alternativo.
- `ModelUnavailableException` — Model `DISABLED`; `ProviderInactiveException` — Provider `INACTIVE`.
- Log de auditoria **sem conteúdo privado** (sem prompt/resposta).

## Testes (SPI-86)

Unitários: routing (`requested` permitido/negado, default, `DISABLED`, `INACTIVE`, ausência de política) + invariantes do `ModelPolicy`.
Integração (`integration`, por cruzar módulos): catálogo ponta a ponta, configuração da política (validação cross-módulo), consulta do modelo permitido, isolamento de tenant e autorização.

## Plano de implementação

1. `V010` (aigateway: providers/models) + `V011` (department: model_policies).
2. `aigateway`: Provider/Model (domínio + persistência + casos de uso + endpoints SPI-81).
3. `department`: `ModelPolicy` + `DepartmentModelPolicyQuery` + `ConfigureDepartmentModelPolicy`.
4. `aigateway`: orquestração da configuração (SPI-82) + routing (SPI-83) + consulta (SPI-84) + erros (SPI-85).
5. Testes (SPI-86) + verificação.

## Decisões que preciso aprovar

1. **Orquestração da validação no `aigateway`** (recomendado) vs. serviço em `platform`.
2. **Adicionar `iam` à allowlist do `aigateway`** (recomendado) — sem isso os endpoints do gateway ficam sem autorização.

## Checks
- `./scripts/check --changed` · `./scripts/check` · `./scripts/check-integration`
