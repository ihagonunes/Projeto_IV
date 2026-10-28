# HANDOFF — Protocolo para o modelo que vai implementar a Sprint 4

Leia este arquivo inteiro antes de qualquer tarefa `TASK-S4-*`. Ele substitui o HANDOFF da Sprint
3 para esta sprint; as regras de lá continuam valendo, com os acréscimos da §4.

## 1. Ordem de leitura (só o necessário)

1. `AGENTS.md`, principalmente a §19.
2. `ai/tasks/sprint-04/INDEX.md`: decisões pendentes, ordem, migrations reservadas e Jira.
3. O `TASK-S4-*.md` da vez e as seções da `SPEC-*` que ele cita.
4. Os ADRs citados. Novos nesta sprint: 40, 41 e 42.
5. Para contexto do que já existe:
   - `docs/review/ESTADO_POS_SPRINT3.md`;
   - `docs/development/MATRIZ_PERMISSOES.md`;
   - `docs/development/CONTRATO_API_FRONT_BACK.md`.
6. O código do módulo proprietário e das dependências diretas.

## 2. Antes de escrever código

- [ ] As dependências do packet estão concluídas no código (`INDEX.md` §3).
- [ ] Os ADRs exigidos estão **ACCEPTED** em `ai/decisions.md` e as decisões S4-Dx do packet foram
      tomadas (`INDEX.md` §2). Se não, **pare** e devolva a tarefa dizendo o que falta.
- [ ] A migration usa o número reservado (`INDEX.md` §4).
- [ ] Linha de base:
  - `mvn -B clean test` (**sempre com `clean`**, ver §4);
  - `npm ci` uma vez;
  - `node --test src/test/frontend/*.test.js`;
  - anotar as contagens.

## 3. Regras de execução (iguais às da Sprint 3)

- **Uma tarefa = um incremento revisável.** Teste negativo primeiro, quando houver.
- **Branch e commit:** `AGENTS.md` §12, com a chave Jira do packet (a primeira; para "a criar", a
  que o responsável informar). **Só crie branch, commit ou push com autorização explícita.**
- **Proibido:**
  - dependência nova (Maven/npm);
  - inserir concessão (`RoleAssignment`) direto em IT; use `src/test/java/br/com/qsoft/governance/iam/support/TestTenants.java` (`ensureTenant`, `ensureMember`, `assignIfAbsent`);
  - confiar em `tenant_id` do cliente;
  - logar token, senha, segredo, prompt ou resposta;
  - alterar o Status de ADR ou a allowlist;
  - mexer no componente de chat do front sem autorização (S4-C3 é da responsável).
- Toda query nativa tenant-aware tem `tenant_id = ?` explícito. No histórico, `company_id = ?`.
- Se o código divergir da spec, **o repositório vence**: faça a menor adaptação segura e reporte.
  Se a divergência tocar segurança, pare e pergunte.

## 4. Lições da Sprint 3 (obrigatórias)

1. **`mvn clean` sempre.** Classes de teste removidas continuam em `target/` e quebram a suíte: em
   27/10, 21 erros falsos vieram de `AuthorizationQueryAdapterTest`/`RegisterUserTest`, que já não
   existem em `src/`.
2. **`TENANT_ADMIN` não lê analytics (V021).** Em testes de leitura analítica (dashboard,
   engajamento, projeção, tentativas), o leitor é **EXECUTIVE** (TENANT) ou MANAGER (DEPARTMENT).
   O admin é usado só nas escritas administrativas, e o teste também prova o **403** do admin
   nas leituras.
3. **Operacional ≠ histórico (ADR-26).** Toda resposta analítica declara `sourceKind`. Nunca some
   as duas fontes.
4. **Rótulo ≠ autorização.** `hierarchyLevel` nunca decide escopo. Toda checagem fina usa
   `visibilityFor`/`isAllowed`. Houve um bug crítico na Sprint 3 por isso (F1 da
   `REVISAO_SPRINT3_A1-A6.md`).
5. **Datas:** o operacional usa dia UTC; o histórico usa dia civil, sem fuso inventado. Testes de
   borda de dia são obrigatórios onde há janela.
6. **Ausente ≠ zero.** Sem fonte ou denominador → `null` + `dataStatus`, nunca 0.
7. **Timeouts dos ITs:** `HistoricalLoaderIT` já passou perto do limite. Para a suíte integral, use
   `MAVEN_ARGS=-Dspring.test.context.cache.maxSize=1 ./scripts/check-integration`. Não aumente
   timeouts sem registrar o motivo.

## 5. Condições de parada (pare e pergunte)

- ADR exigido não está ACCEPTED, ou a decisão S4-Dx do packet está pendente.
- Seria preciso: dependência nova, endpoint fora do `INDEX.md` §5, migration além da reservada,
  evento novo, permissão nova na matriz ou mudança de allowlist.
- Conflito entre a spec e um invariante de segurança, ou com o ADR-16 (privacidade do chat).
- Mexer em arquivos de outra trilha em andamento ou no componente de chat.

## 6. Definition of Done (por tarefa)

- [ ] Critérios do packet atendidos, ou o motivo de cada um não atendido.
- [ ] Verdes: `./scripts/check` (com `clean`), `./scripts/check-integration` (quando houver
      persistência, multi-tenancy ou HTTP) e `node --test` (quando tocar no front).
- [ ] ArchUnit verde.
- [ ] `git diff` só com arquivos da tarefa; sem segredo; sem `.env`.
- [ ] Documentação canônica tocada quando o packet pede.

## 7. Relatório de entrega (copiar e preencher)

```md
### TASK-S4-xx — <título>
- Jira: SPI-..
- Arquivos alterados: <lista>
- Migrations: <versão ou "nenhuma">
- Contratos api.* novos/alterados: <lista>
- Testes adicionados: <IDs EN/CH/PR/PJ + nomes>
- Suíte: unit <n> ✅/❌ · integração <n> ✅/❌ · front <n> ✅/❌ (linha de base: <n>)
- Critérios do packet: <cada um ✅/❌ com motivo>
- Decisões S4-Dx aplicadas: <lista>
- Divergências spec × código: <lista ou "nenhuma">
- Riscos residuais: <lista>
- Ação sugerida no Jira: <fechar / manter / redefinir>
- O que NÃO verifiquei: <lista explícita>
```
