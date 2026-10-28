# TASK-S4-E2 — Módulo `engagement`: V023, domínio puro e adaptadores de fatos

## Metadados
- **Onda:** E · **Ordem:** 2 · **Depende de:** E1
- **Jira:** **SPI-145** (redefinida: a migration é a **V023**, não a V019; tabelas com o prefixo
  `engagement_`)
- **Módulo:** `engagement`
- **Spec:** `SPEC-06` §2, §3, §4

## Critérios de aceitação
- [ ] `V023__engagement_licenses_training_credits.sql` exatamente como na SPEC-06 §2.
- [ ] Domínio (`engagement.domain`, sem Spring/JPA):
  - [ ] `EngagementFacts`, `LicenseFact`, `TrainingFact` e `Coverage`;
  - [ ] `AdoptionCalculator`, `TrainingCrossCalculator` e `CreditsCalculator`, como na SPEC-06 §3.
- [ ] Porta `EngagementFactsPort` com duas implementações:
  - [ ] `OperationalEngagementFacts` (elegíveis IAM, `UsageActivityQuery` e tabelas V023);
  - [ ] `HistoricalEngagementFacts` (`historical.*` com `company_id` explícito e vínculos em lote).
- [ ] Tabelas fixas de mapeamento de `license_status` e `completion_status` do histórico no
      adaptador.
  - O relatório lista os **valores distintos encontrados** no dataset de teste e no banco local,
    se houver acesso.
- [ ] Testes:
  - [ ] unit: EN01, EN02, EN04, EN05 (tabelas de casos);
  - [ ] IT dos dois adaptadores com mini dataset (`historical.*` populado no teste): EN09, EN10.
- [ ] ArchUnit: o `engagement` só depende de `sharedkernel`, `iam.api`, `observability.api` e
      `aigateway.api`. A regra de allowlist existente deve cobrir; conferir.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
