# SPEC-06 — Engajamento: adoção, treinamento × uso e AI Credits (ADR-40)

Base: ADR-17, ADR-18, ADR-19, ADR-26, ADR-27 e **ADR-40**.
Fórmulas de referência: `docs/development/DASHBOARD_INDICADORES_E_CALCULOS.md` §4, §7 e §8.
Tarefas: S4-E1 a S4-E5.

## 1. Fronteiras (allowlist: `engagement → sharedkernel, iam, observability, aigateway`)

| Precisa de | Vem de | Contrato |
|---|---|---|
| Elegíveis (usuários ACTIVE, lotação, rótulo) | `iam.api` | **novo** `IdentityQuery.eligibleUsers(TenantId)` → `List<UserContext>` (só `ACTIVE`; exclui usuários cujas concessões ativas são **só** `TENANT_ADMIN`) |
| Recorte autorizado | `iam.api` | `AuthorizationQuery.visibilityFor(tenant, ator, USAGE, READ)` (existente) |
| Nomes visíveis (lista nominal) | `iam.api` | `UserContext` + nome: acrescentar `name` ao `UserContext` (campo novo, sem quebrar os callers) |
| Atividade operacional | `observability.api` | **novo** `UsageActivityQuery.activeDays(TenantId, Set<UserId>, LocalDate from, LocalDate to)` → `Map<UserId, Set<LocalDate>>` |
| Vínculo histórico | `iam.api` | `HistoricalIdentityQuery.employeeId` (existente) + **novo** `employeeIds(TenantId, Set<UserId>)` → `Map<UserId,String>` (lote) |
| Departamento | `UserContext.departmentId` (lotação IAM atual) | **não** usar `department.api` (fora da allowlist); o front resolve os nomes por `/api/v1/departamentos` |

Regras:
- O `engagement` **nunca** lê tabelas de outro módulo.
- O SQL histórico é nativo, só no adaptador histórico do `engagement`, com `company_id = :tenantId`
  explícito (o mesmo critério do `HistoricalUsageAdapter`).
- O `UsageActivityQuery` (observability) usa `tenant_id` explícito; o dia é calculado em UTC
  (`created_at AT TIME ZONE 'UTC'`).

## 2. Migration V023 (`V023__engagement_licenses_training_credits.sql`)

```sql
CREATE TABLE engagement_ai_licenses (
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    user_id       UUID         NOT NULL,          -- iam, sem FK
    ai_tool_id    UUID,                           -- aigateway, sem FK; nulo = licença genérica da plataforma
    license_type  VARCHAR(40)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,          -- ACTIVE | EXPIRED | REVOKED
    assigned_at   DATE         NOT NULL,
    expires_at    DATE,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_engagement_ai_licenses PRIMARY KEY (id),
    CONSTRAINT ck_eng_lic_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT ck_eng_lic_dates  CHECK (expires_at IS NULL OR expires_at >= assigned_at)
);
-- ADR-17: no máximo uma licença ACTIVE por tenant + usuário + ferramenta
CREATE UNIQUE INDEX uq_eng_lic_active ON engagement_ai_licenses
    (tenant_id, user_id, COALESCE(ai_tool_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'ACTIVE';
CREATE INDEX idx_eng_lic_tenant ON engagement_ai_licenses (tenant_id, status);

CREATE TABLE engagement_training_records (
    id             UUID          NOT NULL,
    tenant_id      UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    course_name    VARCHAR(200)  NOT NULL,
    course_category VARCHAR(80),
    status         VARCHAR(20)   NOT NULL,        -- ENROLLED | COMPLETED | DROPPED
    enrolled_at    DATE,
    completed_at   DATE,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_engagement_training_records PRIMARY KEY (id),
    CONSTRAINT ck_eng_tr_status CHECK (status IN ('ENROLLED','COMPLETED','DROPPED')),
    CONSTRAINT ck_eng_tr_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
CREATE INDEX idx_eng_tr_tenant_user ON engagement_training_records (tenant_id, user_id);

CREATE TABLE engagement_credit_snapshots (
    tenant_id           UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    period              DATE          NOT NULL,    -- dia 1 do mês (YearMonth)
    source_kind         VARCHAR(16)   NOT NULL,    -- OPERACIONAL | HISTORICO
    calculation_version VARCHAR(20)   NOT NULL,    -- 'credits-v1'
    usage_component     NUMERIC(5,2),
    training_component  NUMERIC(5,2),
    license_component   NUMERIC(5,2),              -- nulo = NOT_APPLICABLE
    license_applicability VARCHAR(16) NOT NULL,     -- APPLICABLE | NOT_APPLICABLE
    score               NUMERIC(5,2)  NOT NULL,
    data_status         VARCHAR(20)   NOT NULL,     -- COMPLETE | PARTIAL | INSUFFICIENT_DATA
    closed_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_eng_credit_snapshots PRIMARY KEY (tenant_id, user_id, period, source_kind, calculation_version),
    CONSTRAINT ck_eng_cs_month CHECK (EXTRACT(DAY FROM period) = 1),
    CONSTRAINT ck_eng_cs_source CHECK (source_kind IN ('OPERACIONAL','HISTORICO')),
    CONSTRAINT ck_eng_cs_range CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_eng_cs_lic CHECK ((license_applicability = 'NOT_APPLICABLE') = (license_component IS NULL))
);
CREATE INDEX idx_eng_cs_tenant_period ON engagement_credit_snapshots (tenant_id, period, source_kind);
```

Os snapshots são imutáveis: o adaptador faz `INSERT … ON CONFLICT DO NOTHING`, nunca `UPDATE`.

## 3. Domínio (`engagement.domain`, puro)

```java
record EngagementFacts(Set<UserId> eligible, Map<UserId, Set<LocalDate>> activeDays,
                       Map<UserId, List<LicenseFact>> licenses, Map<UserId, List<TrainingFact>> trainings,
                       Coverage coverage) {}
record LicenseFact(UUID aiToolId, LocalDate assignedAt, LocalDate expiresAt, boolean active) {}
record TrainingFact(String course, LocalDate completedAt) {}   // só concluídos
```

| Cálculo | Regra (ADR-40) |
|---|---|
| `AdoptionCalculator.of(facts, ate)` | janela `[ate−29, ate]`; ativos = elegíveis com algum dia ativo na janela; `taxa = 100·ativos/elegíveis` com 2 casas e HALF_UP; elegíveis = 0 → `taxa=null`, `INSUFFICIENT_DATA`; `licencasOciosas` = licenças aplicáveis no `ate` sem dia ativo do titular na janela |
| `TrainingCrossCalculator.of(facts, ate)` | treinado = ∃ conclusão com `completedAt ≤ ate`; ativo = adoção; 4 quadrantes; contagem e lista de `UserId` por quadrante |
| `CreditsCalculator.of(facts, YearMonth m)` | U = 100·min(diasAtivos(m)/10, 1); T = 100·min(conclusões ≤ fimDoMês, 1); licença aplicável = ACTIVE em fimDoMês e `assignedAt ≤ fimDoMês`; utilizada = titular com ≥ 1 dia ativo em m; L = 100·utilizadas/aplicáveis (sem aplicáveis → NOT_APPLICABLE); score = 0,5U+0,3T+0,2L ou (0,5U+0,3T)/0,8; 2 casas, HALF_UP; `calculationVersion="credits-v1"` |
| `dataStatus` | fonte de licença ou treino indisponível (não implantada ou sem cobertura) → componente `INSUFFICIENT_DATA` e score `PARTIAL`; "sem licença aplicável" **não** é falta de dado (é NOT_APPLICABLE) |

## 4. Adaptadores de fatos

- **`OperationalEngagementFacts`:**
  - elegíveis → `iam.api`;
  - dias ativos → `observability.api.UsageActivityQuery`;
  - licenças e treinos → tabelas V023.
- **`HistoricalEngagementFacts`:**
  - elegíveis: elegíveis IAM que têm vínculo (`employeeIds`);
  - dias ativos: `SELECT employee_id, DATE("timestamp") FROM historical.ai_usage_logs WHERE company_id = ? AND employee_id = ANY(?) AND request_count > 0 AND "timestamp" BETWEEN ? AND ?` (validar o tipo da coluna `"timestamp"` no V014 e fazer o cast como o `HistoricalUsageAdapter`);
  - licenças: `historical.ai_licenses` (datas texto → `LocalDate`; `license_status` mapeado por uma tabela fixa no adaptador: ativo/expirado/revogado; valores desconhecidos → ignorados e contados em `coverage.unmapped`);
  - treinos: `historical.training_records` com `completion_status` mapeado (tabela fixa; registrar os valores distintos encontrados no relatório da tarefa);
  - `ate` padrão = último dia da cobertura de `ai_usage_logs` do tenant.
- **Cobertura** (`Coverage`) devolvida em todas as respostas: `eligibleTotal`, `eligibleLinked`,
  `unmappedLicenseStatus`, `unmappedTrainingStatus`.

## 5. Endpoints (módulo `engagement`, prefixo `/api/v1/engajamento`)

Os três endpoints usam `@RequiresPermission(resource=USAGE, action=READ, targeting=BY_TARGET)` e
aplicam `visibilityFor(USAGE:READ)` sobre os elegíveis, pela lotação IAM:

| Visibilidade | Recorte |
|---|---|
| TenantWide | todos os elegíveis |
| Departments | elegíveis cuja lotação ∈ ids, mais o próprio ator |
| SelfOnly | só o ator |
| None | 403 |

`departmentId` pedido fora do recorte → 403. `fonte` ausente → `OPERACIONAL`.

### 5.1 `GET /api/v1/engajamento/adocao?fonte=&ate=&departmentId=` (SPI-143)

```json
{ "sourceKind":"HISTORICO", "ate":"2026-07-31", "janelaDias":30,
  "elegiveis":279, "ativos":201, "inativos":78, "taxaAdocao":72.04,
  "licencasOciosas":12, "dataStatus":"COMPLETE",
  "porDepartamento":[{ "departmentId":"…", "elegiveis":30, "ativos":25, "taxaAdocao":83.33 }],
  "cobertura":{ "eligibleTotal":300, "eligibleLinked":279, "unmappedLicenseStatus":0, "unmappedTrainingStatus":0 } }
```

### 5.2 `GET /api/v1/engajamento/treinamentos/cruzamento?fonte=&ate=&departmentId=` (SPI-146)

```json
{ "sourceKind":"…", "ate":"…",
  "quadrantes":{ "TREINADO_ATIVO":{"total":120,"usuarios":[{"userId":"…","nome":"…","departmentId":"…"}]},
                 "TREINADO_INATIVO":{…}, "NAO_TREINADO_ATIVO":{…}, "NAO_TREINADO_INATIVO":{…} },
  "listaNominal":true, "cobertura":{…} }
```

- `usuarios` só é preenchido se o ator tiver `USER:READ` cobrindo cada usuário (`isAllowed(USER, READ, OwnedTarget)`); senão vem `[]` e `listaNominal=false`.
- Paginação da lista: no máximo 200 por quadrante, com `truncado:true`.
- Nunca expor nota da prova, salário nem conteúdo.

### 5.3 `GET /api/v1/engajamento/creditos?fonte=&mes=AAAA-MM&departmentId=` (SPI-147)

- Mês fechado: lê o snapshot. Se não existir, calcula, persiste e devolve (`status=FECHADO`).
- Mês corrente: calcula e não persiste (`status=PROVISORIO`).
- Recorte pela visibilidade: SelfOnly → só a própria linha; Departments/TenantWide → linhas
  ordenadas por score desc e `userId` asc.

```json
{ "sourceKind":"…", "mes":"2026-07", "status":"FECHADO", "calculationVersion":"credits-v1",
  "itens":[{ "userId":"…", "nome":"…", "score":78.5, "usage":100.0, "training":100.0,
             "license":null, "licenseApplicability":"NOT_APPLICABLE", "dataStatus":"COMPLETE" }],
  "cobertura":{…} }
```

O ranking e a visão de equipe (SPI-149 a 151) são da Sprint 5 e não entram aqui.

## 6. Seeder de demonstração (SPI-145)

- `EngagementDemoSeeder` (`@ConditionalOnProperty app.bootstrap.demo-data=true`), idempotente.
- Para os usuários do tenant demo, cria licenças (parte sem licença, para exercitar NOT_APPLICABLE)
  e treinamentos (concluídos, inscritos e desistentes), distribuídos de modo que os 4 quadrantes
  fiquem populados.
- Usa só casos de uso do `engagement`; nada de SQL direto em tabela de outro módulo.

## 7. Não bloqueio (SPI-148)

- **ArchUnit:** nenhuma classe de `..aigateway..`, `..chat..`, `..agentpolicies..` ou `..iam..`
  depende de `..engagement..`.
- **IT:** um usuário com Credits 0 (sem uso, sem treino, sem licença) envia mensagem no chat
  (STUB) → 201.
- **IT:** apagar todas as linhas de `engagement_*` não altera a resposta de
  `/api/v1/ai-gateway/models/permitido` nem do chat.

## 8. Testes

| ID | Cenário | Tipo |
|---|---|---|
| EN01 | Adoção: janela de 30 dias inclusiva (dia `ate−29` conta; `ate−30` não) | unit |
| EN02 | Elegíveis = 0 → `taxa=null`, INSUFFICIENT_DATA | unit |
| EN03 | `TENANT_ADMIN` puro fora dos elegíveis; usuário INACTIVE fora | unit + IT |
| EN04 | Quadrantes: treino concluído depois de `ate` não conta | unit |
| EN05 | Credits: U satura em 10 dias; T binário; L = NOT_APPLICABLE normaliza por 0,8; arredondamento | unit (tabela de casos) |
| EN06 | Snapshot imutável: segunda chamada do mesmo mês não altera valores, mesmo com fatos novos | IT |
| EN07 | MANAGER(A) só vê A + ele mesmo; `departmentId=B` → 403; COLLABORATOR só a própria linha | IT |
| EN08 | Lista nominal vazia para quem não tem `USER:READ` (COLLABORATOR); LEADER vê só o próprio depto | IT |
| EN09 | Outro tenant: dados nunca aparecem (operacional e histórico) | IT |
| EN10 | Fonte histórica: funcionário sem vínculo fora; cobertura reporta | IT (Testcontainers + mini dataset em `historical.*`) |
| EN11 | `fonte` nunca mistura: uso operacional não muda a resposta histórica e vice-versa | IT |
| EN12 | Não bloqueio (§7) | ArchUnit + IT |
