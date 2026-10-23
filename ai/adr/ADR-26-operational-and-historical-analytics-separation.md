# ADR-26 — Operational and Historical Analytics Separation

## Status

ACCEPTED

## Contexto

O dataset QSOFT possui semântica histórica diferente das entidades operacionais criadas pela aplicação.

## Decisão

Importar dados históricos em historical_* no PostgreSQL e acessá-los por HistoricalUsageAdapter/HistoricalEngagementAdapter/HistoricalGovernanceAdapter. Não converter histórico artificialmente em aggregates operacionais nem criar FKs para tabelas operacionais.

## Alternativas consideradas

Mesclar histórico e operação nas mesmas tabelas; migrar cada linha histórica para entidade de domínio.

## Consequências

Preserva significado e permite analytics híbrido, ao custo de adapters/read models adicionais.

## Emenda 23/10/2026 — identidade legada e escopo (FEAT-DASH-001 local)

O login usa app_user e as concessões IAM atuais. Importar as identidades de
iam_users da fonte congelada preservando email, UUID, cargo e status, com tenant
remapeado pelo manifesto histórico reconciliado. Não autenticar no banco antigo.
A importação é uma operação local explícita e transacional.

IAM mantém iam_legacy_user_links; department mantém department_legacy_links.
Cada módulo expõe sua Query Port pública; observability resolve IDs textuais por
essas portas e aplica o RBAC atual. As únicas FKs novas pertencem ao próprio
módulo. Nenhuma FK aponta para o histórico ou outro módulo.

SELF filtra employee_id; DEPARTMENT filtra departamentos concedidos mais o próprio
histórico; TENANT filtra company_id. Agregados e metadados respeitam o recorte.
/historical/me exige vínculo pessoal inclusive para administrador. userId
solicitado é validado antes da consulta pelo resolvedor existente.

Logs continuam históricos; não viram chamadas operacionais. Não inferir autorização
apenas de hierarchy_level: o importador associa papéis existentes sem alterar
permissões. Administrador legado ambíguo é rejeitado. Concessão importada revogada
não é restaurada por reexecução.

Alternativa descartada: trocar nomes de tabelas ou autenticar no schema legado,
pois contornaria o modelo IAM atual. A consequência é manter vínculos explícitos
e um procedimento auditável de importação.
