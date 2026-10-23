# Validação, evidências e pendências

Estado consolidado em 23/10/2026. Complementa
[Dashboard, acesso e administração](DASHBOARD_E_ORCAMENTOS.md) e
[Banco legado, migração e identidades](BANCO_E_IMPORTACAO_HISTORICA.md).
BUGFIX-DB-001 e FEAT-DASH-001 são identificações locais, não issues Jira remotas.

## Resultado mais recente

| Verificação | Resultado |
|---|---|
| scripts/check --changed e scripts/check | 817 testes unitários/ArchUnit passaram, sem falhas, erros ou skips |
| Frontend Node e ferramentas locais | 54 testes passaram |
| Setup automático da equipe | LocalSetupIT passou com PostgreSQL descartável e JAR real: instalação vazia, restauração de dump, importação e reexecução |
| Integração completa após V021 | 170 casos executados; 14 falharam por fixtures que ainda usavam administrador como leitor analítico |
| Reexecução das fixtures corrigidas | 23 testes de AIGatewayAttemptsIT, DepartmentBudgetIT e UsageLoggingIT passaram; os outros 147 já haviam passado na execução completa |
| Integração anterior à V021 | 170 casos passaram na etapa de importação de identidades |
| CI remoto | Não executado/verificado |

A execução completa e a repetição das três classes são evidências complementares;
não houve uma nova execução integral única verde depois da atualização das fixtures.
As correções mantiveram administrador nas escritas, usaram executivo nas leituras
e verificaram 403 para administrador. Nenhuma permissão foi relaxada para passar teste.

HistoricalLoaderIT passou em 215 segundos na última execução completa, próximo
do timeout de 240 segundos. Houve timeout em execução anterior; acompanhar a
estabilidade antes de alterar limites. Integração foi executada com
MAVEN_ARGS=-Dspring.test.context.cache.maxSize=1 para limitar contextos retidos,
sem alterar produção ou desativar testes.

## Verificações funcionais realizadas

- Login por cookie retorna 200 sem JWT no corpo; cookie HttpOnly/Strict, Secure
  em produção. Token adulterado retorna 401, CSRF 403 e logout 204 expira o cookie.
- /dashboard abre o perfil correspondente às concessões validadas. Query string
  ou perfil visual não concede acesso. Navegação do onboarding respeita os papéis.
- Colaborador importado: 6.618 requisições entre 01–31/07/2026 e um funcionário,
  iguais ao SQL filtrado por tenant e legacy_employee_id. Injeção de IDs não
  altera o histórico pessoal; outro usuário retorna 403; login inativo retorna 401.
- Gestor fica limitado aos departamentos concedidos; executivo consulta a empresa.
  Metadados/cobertura respeitam o recorte; outro tenant não entra nos agregados.
- Administrador: login/cadastro/branding 200; qualquer perfil de dashboard volta
  ao onboarding. Seis endpoints de métricas e o acesso Bearer direto ao Spring
  retornam 403. Convite criado/revogado e logo salva/relida, com restauração da
  logo original. Executivo mantém dashboard 200 e recebe 403 ao salvar logo.
- Fonte antiga preservada; snapshot reconciliado com as 55 tabelas originais.
  Destino em V021, Hibernate validate e boot conferidos; V008 atual mantém
  checksum -1535036762. Carga histórica idêntica retorna ALREADY_LOADED.
- Importação de identidades: 2.356 vínculos, 161 departamentos e 198 usuários
  inativos preservados. Reexecução não duplica; concessão revogada não é restaurada.
  Login bootstrap anterior continua válido. V021 altera intencionalmente apenas
  as permissões analíticas do papel administrativo.

Referência empresarial conferida: NexoCode Solucoes, julho/2026, 372.716 requisições,
279 funcionários com uso, 11 departamentos e 11 modelos. Cobertura de 01/06/2025 a
31/07/2026. Há 5.665 registros sem preço/tokens completos; custo indicado como parcial.
Esses totais são evidências da carga, não expectativas fixas de teste.

## Evidências locais

Arquivos abaixo estão em target, ignorados pelo Git; backups e hashes privados
ficam nos diretórios externos indicados no documento de banco.

| Grupo | Evidências principais |
|---|---|
| Checks finais | admin-separation-check-changed.log, admin-separation-check.log |
| Integração e repetição | admin-separation-integration.log, admin-separation-recheck.log |
| Frontend e fluxo administrativo | admin-separation-frontend.log, admin-separation-smoke.json |
| Importação e isolamento de identidades | legacy-full-integration.log, legacy-identities-loaded.json, legacy-identities-retry.json, legacy-live-smoke.json |
| Navegação por papel | test-levels-verified.json; cenários automatizados em onboarding.test.js e browser-session.test.js |
| Setup automático | team-setup-frontend-final.log, team-setup-recheck.log, team-setup-default-build.log |

Backups anteriores às mudanças de identidade e administração estão em
20261023_identities e 20261023_admin_separation. Não versionar arquivos privados,
credenciais, hashes, CSVs, relatórios de execução ou documentos da pasta output.

## Como verificar novamente

Pré-requisitos: Java 21, Node 24, PostgreSQL real/psql e Docker para Testcontainers.
No Windows, usar Git Bash para os scripts quando necessário. Testes de integração
usam bancos descartáveis; não apontar fixtures para o banco fonte.

```sh
./scripts/check --changed
./scripts/check
./scripts/check-integration
node --test src/test/frontend/*.test.js
```

O novo LocalSetupIT exercita banco vazio, reexecução, recusa de destino alheio,
recusa de concessão revogada, carga com histórico e repetição dessa carga.
Confere contas sem duplicação, usuário inativo, vínculos e preservação
da origem. No teste local foi habilitada também restauração de archive PostgreSQL
18 (`QSOFT_SETUP_TEST_DUMP=true`); sem essa variável o CI usa snapshot descartável
já preparado, sem exigir pg_dump compatível no host. O limite de 900 segundos
é desse novo teste completo; não altera o timeout de HistoricalLoaderIT.

O comando padrão do Windows também passou separadamente com build Maven
automático em banco local descartável, V021 e cinco acessos. O instalador não
executa testes durante o build (`-DskipTests`); as validações acima são separadas.
Pré-requisitos, configuração e comandos de instalação estão no início do
[guia de banco](BANCO_E_IMPORTACAO_HISTORICA.md#instalação-da-equipe--comece-aqui).

Maven não executa automaticamente os testes frontend. Os testes abrangem sessão,
CSRF, autorização, isolamento, filtros, cálculos, Flyway, constraints, importação
real, rollback, idempotência, revogação e branding sem sucesso fictício.
Os registros de execução não substituem validação visual de todos os fluxos.

## Pendências importantes

1. Rodar novamente a suíte integral com as fixtures finais e verificar CI remoto
   quando houver publicação; acompanhar o tempo de HistoricalLoaderIT.
2. Validar visualmente orçamentos: troca de mês, modal, cancelar/salvar, histórico,
   limiares, zero/ausente, concorrência e acessibilidade da lista de alertas.
3. Conferir filtros combinados, período vazio/invertido e custo parcial pelos
   perfis reais. Para futuras cargas, validar manifesto e vínculos explícitos.
4. Integrar faturamento separado por moeda e definir vínculo entre centros de
   custo e departamentos antes de usar orçamento histórico na operação.
5. Integrar adoção, capacitação, créditos, governança e retorno conforme fontes,
   ADRs e fórmulas aprovadas. Sem fonte, manter indisponível; não deduzir salários,
   produtividade, qualidade ou economia realizada somente dos tokens.
6. Substituir as identificações provisórias pelas chaves Jira reais quando criadas.

O documento preexistente DASHBOARD_INDICADORES_E_CALCULOS.md mantém fórmulas e
lacunas dos demais indicadores. ADRs, contratos e documentação canônica em ai/
continuam preservados. Estes três consolidados reúnem os relatos das nossas
entregas; novas atualizações devem ser integradas às seções correspondentes.
