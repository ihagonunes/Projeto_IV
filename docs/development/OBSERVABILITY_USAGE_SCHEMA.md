# Schema do Usage Log (observability)

Documentação do registro operacional de uso de IA. Vinculado a **ADR-13** (Usage
Observability), **ADR-26** (separação operacional × histórico), **ADR-29**
(persistência modular) e **ADR-03** (multi-tenancy).

## Origem única

`AIRequestCompleted` — evento de domínio emitido pelo **AI Gateway** ao concluir
uma requisição — é a **única origem operacional** de `UsageRecord` (ADR-13).

```text
completion (Gateway)
   → calcula estimatedCost UMA vez (SPI-72)
   → publica AIRequestCompleted   [sharedkernel/event]
   → observability consome como EFEITO SECUNDÁRIO (SPI-70/71)
   → persiste UsageRecord (idempotente por requestId)
```

Não existe **endpoint público genérico** de ingestão de logs (SPI-71).

## Tabela `observability_usage_records`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | UUID (PK) | Identidade do registro |
| `request_id` | UUID (**UNIQUE**) | **Chave de idempotência** (ADR-13) |
| `tenant_id` | UUID NOT NULL | Isolamento; **sem FK** cross-module (ADR-29) |
| `department_id` | UUID | Referência por ID (sem FK) |
| `user_id` | UUID | Referência por ID (sem FK) |
| `model_id` | UUID | Referência por ID (sem FK) |
| `provider_id` | UUID | Referência por ID (sem FK) |
| `input_tokens` / `output_tokens` | BIGINT NOT NULL | Contagem de tokens |
| `estimated_cost` | NUMERIC(15,6) NOT NULL | Custo calculado **pelo Gateway** (não recalculado aqui) |
| `latency_ms` | BIGINT | Latência da requisição |
| `status` | VARCHAR(30) NOT NULL | Estado operacional (ex.: `COMPLETED`) |
| `created_at` | TIMESTAMPTZ NOT NULL | Momento do registro |

**Não há** prompt, resposta, documento ou segredo (ADR-13).

## Índices (SPI-76)

| Índice | Filtros que atende |
|---|---|
| `(tenant_id, created_at DESC)` | período + tenant |
| `(tenant_id, department_id, created_at DESC)` | período + tenant + departamento |
| `(tenant_id, user_id, created_at DESC)` | período + tenant + usuário |

## API de consulta

`GET /api/v1/observability/usage` — paginada (ADR-28), filtros `departmentId`,
`userId`, `from`, `to`. Requer `USAGE:READ` (ADR-34). O tenant vem **sempre** do
contexto autenticado (ADR-03).

## Preço e custo (SPI-72)

O preço fica no catálogo do Gateway (`aigateway_models.input_price_per_1k` e
`output_price_per_1k`, migration `V012`). O cálculo é:

```text
estimatedCost = (inputTokens/1000 * inputPrice) + (outputTokens/1000 * outputPrice)
```

O `observability` **apenas persiste/agrega** — nunca recalcula (ADR-13).

## Fora do escopo operacional

O histórico analítico da QSOFT vive em tabelas `historical_*`, **sem FK** para as
tabelas operacionais (ADR-26), acessado por um read model. A carga do dataset
sintético (SPI-75) é tratada à parte.

## Histórico analítico (SPI-75)

O dataset sintético da QSOFT (17 tabelas: `ai_usage_logs`, `ai_billing`,
`employees`, `projects`, `security_events`, …) é carregado no **schema
`historical`** (migration `V014`), **sem FK** para as tabelas operacionais
(ADR-26). É massa de teste/BI — não simula aggregates operacionais.

**Carga:** `scripts/db/load-historical.ps1 -DumpPath <dump.sql>` — restaura o
dump em um banco temporário e copia cada tabela para `historical.<tabela>`.

**Isolamento de tenant:** o histórico usa `company_id` (dataset), que **não**
corresponde ao `tenant_id` da plataforma. O loader aceita
`-CompanyMapping "COMP-A=<tenant-uuid>"` e **reescreve `company_id` para o
tenant_id**, de forma que o read model possa filtrar por tenant.

**Acesso:** `HistoricalUsageAdapter` (`observability`) — consulta **nativa**
sobre `historical.ai_usage_logs` **sempre com filtro explícito por
`company_id = :tenantId`** (ADR-03). O histórico é somente leitura.

