# TASK-S4-H1 — Linha de base verde, compose com `JWT_SECRET` e testes do front no CI

## Metadados
- **Onda:** H (partida) · **Ordem:** 1 · **Depende de:** —
- **Jira:** SPI-173
- **Área:** build, `compose.yaml`, `.github/workflows/ci.yml`
- **Pendências de origem:**
  - `docs/development/VALIDACAO_E_PENDENCIAS.md` (pendência 1);
  - `docs/review/AUDITORIA_DOCUMENTACAO.md` (item 2).

## Objetivo
Começar a sprint de uma linha de base **comprovada**:
- uma execução integral verde, unit + IT + front;
- `docker compose up` funcionando;
- testes do front rodando no CI.

## Critérios de aceitação
- [ ] **Sempre `mvn clean`.** Em 27/10, `mvn test` sem `clean` falhou: 21 erros/falhas vieram de
      classes antigas em `target/` (`AuthorizationQueryAdapterTest`, `RegisterUserTest`). Os
      scripts `scripts/check` e `scripts/check-integration` passam a usar `mvn -B clean …`.
- [ ] Execução integral única e verde de `./scripts/check-integration`, com a contagem registrada no
      relatório.
  - Em 27/10: unit 817/817 ✅; IT 168/171. As 3 falhas (`HistoricalLoaderIT`,
    `LegacyIdentityImportIT`, `LocalSetupIT`) são `spawn psql ENOENT`: falta o cliente PostgreSQL no
    host.
  - Pré-requisito: `psql`/`pg_restore` 18 no PATH.
  - No CI, instalar `postgresql-client` no job de integração, ou documentar por que esses ITs ficam
    fora. **Não** desabilitar os testes.
  - Se algum IT falhar, corrigir a **fixture**, nunca relaxar permissão.
  - Registrar o tempo do `HistoricalLoaderIT`.
- [ ] `npm ci && node --test src/test/frontend/*.test.js` verde. Em 27/10, falhou só por falta de
      `node_modules`.
- [ ] **Mudança de configuração, exige a decisão S4-D9:**
  - [ ] `compose.yaml` repassa `JWT_SECRET` (e, opcionais, `APP_BOOTSTRAP_DEMO_DATA` e
        `APP_BOOTSTRAP_DEMO_PASSWORD`) ao serviço `backend`;
  - [ ] o `HOW_TO_RUN.md` remove o aviso de limitação.
- [ ] **CI:** job `frontend` em `.github/workflows/ci.yml` com `actions/setup-node` (versão do
      `engines`/README), `npm ci` e `node --test`. Sem dependência npm nova.
- [ ] Nada de código de produção alterado.

## Checks
- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js`
