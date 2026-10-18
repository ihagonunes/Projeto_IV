# TASK-S3-E1 — Base de conhecimento, recuperação segura e seeder de políticas

## Metadados
- **Onda:** E (agente de políticas) · **Depende de:** **ADR-35 ACCEPTED (opção A)**, A2
- **Jira:**
  - SPI-128 (redefinir: "schema de conhecimento com busca nativa", em vez de pgvector);
  - SPI-129 (redefinir: seeder sem embeddings);
  - SPI-130.
- **Módulo proprietário:** `agentpolicies`
- **Spec:** `SPEC-05-agente-politicas.md` §1, §2, §5, §6 (isolamentos, sem correspondência, divisão em trechos)

## Pré-condição
ADR-35 ACCEPTED com a opção A (18/10/2026). Não adicione Spring AI, pgvector nem embeddings.

## Critérios de aceitação
- [ ] `V019__agentpolicies_knowledge.sql` exatamente como na SPEC-05 §1, sem `CREATE EXTENSION`.
- [ ] Domínio de documento, versão (ADR-25: no máximo uma ACTIVE) e trecho, sem Spring/JPA.
- [ ] `KnowledgeAccessService` em `agentpolicies.application`, com o adaptador nativo da SPEC-05 §2:
  - [ ] `tenant_id` explícito nas três tabelas;
  - [ ] lotação do usuário vinda do `IdentityQuery`.
- [ ] `AgentPoliciesDemoSeeder` (SPEC-05 §5):
  - [ ] idempotente;
  - [ ] textos originais, em pt-BR;
  - [ ] divisão em trechos por parágrafo (até cerca de 800 caracteres).
- [ ] Testes:
  - [ ] IT de isolamento de tenant;
  - [ ] IT de isolamento departamental;
  - [ ] usuário sem lotação → só documentos `tenant_wide`;
  - [ ] teste unitário da divisão em trechos;
  - [ ] teste de schema (sem extensão).

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
