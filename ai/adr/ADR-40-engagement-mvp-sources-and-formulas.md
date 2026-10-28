# ADR-40 — Engagement no MVP: fontes, população elegível e parâmetros de adoção, treinamento e AI Credits

## Status

ACCEPTED (2026-10-28), com as recomendações das decisões S4-D2 a S4-D13 (`ai/tasks/sprint-04/INDEX.md` §2).

## Contexto

O módulo `engagement` existe só como esqueleto (`engagement/*` vazio).
A Sprint 4 pede quatro entregas:
- adoção (SPI-143/144);
- treinamento × uso (SPI-145/146);
- AI Credits (SPI-147);
- garantia de que AI Credits não bloqueia (SPI-148).

Os ADRs existentes fixam a forma, mas não os parâmetros:

- ADR-17: o `engagement` é dono de `AILicense`/`TrainingRecord`. A população elegível vem **só** do
  IAM. O histórico é lido por um adaptador próprio.
- ADR-18: ativo é quem tem ≥ 1 uso nos últimos 30 dias corridos.
- ADR-19: Credits = 0,50 U + 0,30 T + 0,20 L (0–100, não bloqueante), com normalização quando a
  licença é NOT_APPLICABLE. Saturação e normalização não estão definidas.
- ADR-26: operacional e histórico nunca se misturam.

Os dados disponíveis estão em dois lugares:
- **operacional:** `observability_usage_records` (uso) e `app_user`/concessões (elegíveis); não há
  licenças nem treinamentos;
- **histórico:** `historical.employees`, `ai_usage_logs`, `ai_licenses` e `training_records`
  (NexoCode, jun/2025–jul/2026), ligado aos usuários atuais por `iam_legacy_user_links` (V020).

`docs/development/DASHBOARD_INDICADORES_E_CALCULOS.md` §4, §7 e §8 lista as fórmulas e as lacunas.

## Decisão

1. **Duas fontes, nunca misturadas** (ADR-26). Todo endpoint de engajamento recebe
   `fonte=OPERACIONAL|HISTORICO` e devolve `sourceKind`. Nenhuma resposta soma fatos das duas.
2. **Fatos por porta, cálculo no domínio.** As fórmulas ficam em `engagement.domain`, puras, sem
   Spring nem I/O. Duas implementações de `EngagementFactsPort` alimentam o domínio:
   - `OperationalEngagementFacts`: elegíveis via `iam.api`; atividade via o novo
     `observability.api.UsageActivityQuery`; licenças e treinamentos das tabelas do `engagement`
     (V023).
   - `HistoricalEngagementFacts`: leitura nativa de `historical.*` com `company_id` = tenant
     autenticado. Funcionários são resolvidos para usuários atuais **só** pelos vínculos
     (`iam.api.HistoricalIdentityQuery`).
3. **População elegível** = usuários IAM `ACTIVE` do tenant, dentro do recorte autorizado. Na fonte
   histórica, só os elegíveis com vínculo legado. Contas `INACTIVE` e usuários só com
   `TENANT_ADMIN` ficam fora.
4. **Adoção (ADR-18).** Data de corte `ate` (padrão: hoje UTC; no histórico, o último dia da
   cobertura):
   - Ativo = elegível com ≥ 1 uso em `[ate − 29 dias, ate]`.
   - `taxaAdocao` = 100 × ativos / elegíveis, com denominador = **elegíveis** (decisão S4-D3).
   - `licencasOciosas` = licenças ACTIVE sem uso do titular na janela; reportado à parte, nunca
     como denominador.
   - Sem elegíveis → `null` + `INSUFFICIENT_DATA`.
5. **Treinado (decisão S4-D4).** Usuário com ≥ 1 registro de treinamento concluído até `ate`. O
   mapeamento dos `completion_status` do histórico para concluído é uma tabela fixa no adaptador,
   versionada.
6. **Quadrantes** treinado × ativo: TREINADO_ATIVO, TREINADO_INATIVO, NAO_TREINADO_ATIVO e
   NAO_TREINADO_INATIVO.
   - A lista nominal só aparece para quem tem `USER:READ` no recorte (decisão S4-D5).
   - Para os demais, só as contagens.
   - Nenhum dado de conversa, salário ou nota de prova é exposto.
7. **AI Credits (ADR-19).** Calculado por usuário, por mês (`YearMonth`, alinhado ao ADR-27):
   - U = 100 × min(diasComUso no mês / 10, 1);
   - T = 100 × min(conclusões até o fim do mês / 1, 1);
   - L = 100 × licençasUtilizadas / licençasAplicáveis.
   - Sem licença aplicável → L = NOT_APPLICABLE e Credits = (0,50 U + 0,30 T) / 0,80.
   - Os parâmetros são constantes de domínio versionadas (`calculationVersion = "credits-v1"`), sem
     configuração em runtime.
   - Ausência de fonte ≠ zero: o componente ganha `dataStatus` próprio.
8. **Snapshot.** Os Credits de um mês fechado são persistidos (V023) por
   (tenant, usuário, mês, fonte, versão) e não mudam depois. O mês corrente é calculado na leitura,
   com `status = PROVISORIO`.
9. **Não bloqueio (ADR-19, SPI-148).** Nenhum código de `aigateway`, `chat`, `agentpolicies` ou
   `iam` depende de `engagement`. Uma regra ArchUnit garante isso.

## Alternativas consideradas

| Alternativa | Por que foi descartada |
|---|---|
| Só fonte operacional (leitura literal do Jira) | Na demo, quase todo o uso é histórico; os painéis ficariam vazios. |
| Só histórico | Não reflete o uso real da plataforma; viola a intenção da SPI-143 ("bater com UsageRecord"). |
| Denominador = usuários licenciados (texto da SPI-143) | Contradiz ADR-17/18 e o documento de indicadores; quem não tem licença sumiria da adoção. |
| Credits configurável em runtime | Rejeitado pelo próprio ADR-19. |
| Calcular Credits a partir das conversas | Viola o ADR-16. |

## Consequências

- **Novo contrato em `observability.api`:** `UsageActivityQuery`, com dias com uso por usuário num
  intervalo, sempre com `tenant_id`.
- **`iam.api` ganha** a listagem de elegíveis (`IdentityQuery.eligibleUsers`).
- **Uma migration (V023):** `engagement_ai_licenses`, `engagement_training_records` e
  `engagement_credit_snapshots`.
- **Três endpoints:** adoção, cruzamento de treinamento e créditos.
- Os parâmetros de Credits são uma escolha de produto: trocá-los exige nova `calculationVersion`,
  não editar a v1.
- O histórico usa os vínculos V020: funcionários sem vínculo ficam fora (a cobertura é reportada).
