# Banco legado, migração e identidades

Estado consolidado em 23/10/2026. `BUGFIX-DB-001` e `FEAT-DASH-001` são
identificações locais autorizadas, sem issues Jira remotas.
[Funcionalidades](DASHBOARD_E_ORCAMENTOS.md) ·
[Validação e pendências](VALIDACAO_E_PENDENCIAS.md).

## Instalação da equipe — comece aqui

Cada integrante usa seu próprio banco e configuração. O Git leva migrations e
ferramentas; o dataset deve ser compartilhado separadamente, como dump do snapshot
legado validado. O instalador não precisa das credenciais, UUIDs ou manifestos da
máquina de Vulpi. Não apontar o setup para um banco já utilizado pela aplicação.

### 1. Pré-requisitos e configuração

Instalar Java/JDK 21, Maven, Node 24 e PostgreSQL local. Deixar `java`, `jar`,
`mvn`, `psql` e `pg_restore` no PATH; configurar JAVA_HOME para o JDK 21.
Para dump gerado pelo PostgreSQL 18, usar cliente pg_restore 18 ou compatível.
O usuário do PostgreSQL precisa criar bancos ou ser dono de bancos vazios já
criados. Docker é necessário apenas para testes de integração.

Na raiz do projeto:

```sh
npm ci
npm run setup:local -- --init-env .env.team.local
```

Editar `.env.team.local`: DB_USER e DB_PASSWORD devem corresponder ao PostgreSQL
da pessoa; DB_NAME identifica um banco novo, por exemplo `qsoft_team_dev`.
Conferir SERVER_PORT (backend), PORT (frontend), API_PROXY_TARGET e SPRING_API_URL.
Se mudar a porta do backend, mudar também as duas URLs. Usar portas livres.
O arquivo gerado contém JWT próprio, NODE_ENV=development e senha de contas
mockadas em SETUP_MOCK_PASSWORD (inicialmente admin123). Não compartilhar esse
arquivo nem substituir o JWT pelo de outra pessoa.

### 2. Preparar banco e dados em um comando

**Com o dataset (recomendado para visualizar os dashboards):** criar o arquivo
privado `.env.source.local` com a conexão do PostgreSQL que receberá o snapshot:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=qsoft_source_snapshot
DB_USER=SEU_USUARIO_POSTGRES
DB_PASSWORD=SUA_SENHA_POSTGRES
```

O snapshot deve ter nome diferente do destino. Ele será criado e restaurado
somente se estiver vazio. Usar arquivo archive gerado por `pg_dump -Fc`, lido
por pg_restore; dump SQL em texto não é esse formato. O dump esperado contém as 17 tabelas staging e as
identidades legadas public.iam_users/public.department_departments; um arquivo
com apenas parte do dataset não basta para a importação de identidades.
Substituir CAMINHO_DO_DUMP pelo caminho real do arquivo compartilhado:

```sh
npm run setup:local -- --env .env.team.local --confirm-target qsoft_team_dev --source-env .env.source.local --source-dump "CAMINHO_DO_DUMP"
```

O comando cria destino e snapshot separados, restaura o dump, compila o backend,
aplica Flyway, provisiona tenants, importa/reconcilia histórico, importa
identidades e prepara cinco acessos de teste. Usa um backend temporário local,
que é encerrado ao terminar; não encerra nem reaproveita servidores existentes.
Não executa DROP, clean, repair, baseline ou restauração sobre tabelas existentes.

Se o snapshot já estiver restaurado, ajustar DB_NAME em `.env.source.local` e
executar o mesmo comando sem --source-dump. Também é possível selecionar outro
snapshot com --source-db NOME. Essa fonte será somente lida.

**Sem dataset (somente schema e acessos locais):**

```sh
npm run setup:local -- --env .env.team.local --confirm-target qsoft_team_dev
```

Nesse modo são criados um tenant, um departamento e cinco contas, sem inventar
logs de consumo. O histórico pessoal depende da importação/vínculo e ficará
indisponível. Para instalar com outra fonte posteriormente, usar novo banco e
work-dir; não misturar instalações parciais de contextos diferentes.

### 3. Acessos e inicialização normal

O resultado fica em `tmp/setup-qsoft_team_dev/accounts.local.json`: emails
de administrador, executivo, gestor, líder e colaborador, com informação sobre
vínculo histórico. A senha é a definida em SETUP_MOCK_PASSWORD do arquivo local.
Com a fonte, o instalador prefere usuários importados com consumo e senha mock
conhecida; mantém hashes válidos da fonte e usuários inativos. Se não houver um
colaborador elegível, avisa e para, sem sobrescrever credenciais ou inventar vínculo.

Iniciar o backend em um terminal:

```powershell
# Windows / PowerShell
./scripts/run-local.ps1 -EnvFile .env.team.local
```

```sh
# Linux / macOS / Git Bash
./scripts/run-local.sh .env.team.local
```

Em outro terminal, iniciar o frontend com o mesmo arquivo:

```powershell
$env:QSOFT_ENV_FILE='.env.team.local'
npm start
```

```sh
QSOFT_ENV_FILE=.env.team.local npm start
```

Abrir http://127.0.0.1:3000/login (ou a PORT configurada). Para dados da carga
atual, selecionar julho/2026 na sidebar. O administrador não tem dashboard.

### Reexecução e diagnóstico

Repetir o mesmo comando, arquivo e work-dir é seguro: carga idêntica não duplica,
senhas não são redefinidas e concessões revogadas não são restauradas. Preservar
tmp/setup-qsoft_team_dev: contém estado, manifesto, chave local e hash privado,
e sobrevive a `mvn clean`. O diretório já é ignorado pelo Git.
Não copiar esse diretório entre máquinas. Se foi perdido em um banco já ocupado,
o instalador recusa assumir sua propriedade; usar um novo destino vazio.

Logs privados: build.log, backend.log, restore.log e password-helper.log nesse
diretório. Não contêm saídas intencionais de senhas/tokens pelo instalador.
Se uma restauração falhar parcialmente, escolher novo snapshot, destino e work-dir
vazios; nenhuma limpeza automática será feita. Se a porta estiver ocupada, parar o serviço manualmente
ou escolher outra porta na configuração. Não usar o comando no banco antigo.

Opções para instalações com ferramentas fora do PATH: --java CAMINHO, --maven
CAMINHO, --psql CAMINHO e --pg-restore CAMINHO. --work-dir pode escolher diretório
privado externo ou target/tmp. --skip-build true reutiliza um JAR já compilado;
o instalador confere as migrations e rejeita JAR desatualizado. Em uma instalação
normal, deixar o build automático habilitado.

Ao publicar, incluir package.json, scripts/setup-local.js,
scripts/db/LocalMockPassword.java, importadores, migrations V020/V021 e todo o
código/testes relacionado. Conferir `git status` e `git diff --cached`: há
arquivos novos e alterações parciais que não são incluídos por um commit apenas
do staging antigo. Não adicionar .env, target, dumps ou arquivos privados.

## Diagnóstico e abordagem adotada

O banco antigo `projeto_integrado_iv` tinha 55 tabelas: 17 em staging e 38 em
public, sem schema historical. Misturava dataset bruto, transformações legadas
e parte do operacional. Não foi comprovado quem realizou essas transformações.

O Flyway falhava na V008: checksum aplicado `1920255488`, arquivo atual
`-1535036762`. As três colunas de branding já existiam; adicionar IF NOT EXISTS
às três instruções reproduzia o checksum antigo. Havia também divergências de schema:

| Origem | Aplicação atual |
|---|---|
| iam_users e app_user com identidades diferentes | JPA autentica em app_user |
| ai_gateway_* | aigateway_* com estrutura própria |
| public.historical_ai_usage_logs | historical.ai_usage_logs |
| observability_usage_records vazia e incompatível | Contrato da V013, incluindo custo, data e request_id UUID |
| department_departments incompleta | Campos de descrição, liderança e orçamento da V009 |
| department_budgets | department_monthly_budgets, com outra semântica |
| Flyway registrado até V008 | Estruturas esperadas de RBAC/convites também divergiam |

Trocar nomes ou reparar checksum isoladamente não resolveria o problema.
A abordagem 3 preservou a origem, restaurou um snapshot e criou banco novo pelas
migrations atuais. Primeiro carregou staging → historical; depois importou as
identidades explicitamente autorizadas para IAM. Logs não viraram chamadas
operacionais. V001–V019 permaneceram intactas; V020 acrescentou vínculos e V021
separou permissões administrativas e analíticas. Origem não recebeu repair,
clean, baseline, DDL ou DML.

## Ambiente, execução e recuperação

| Recurso | Referência local |
|---|---|
| Origem preservada | localhost:5432/projeto_integrado_iv |
| Banco da aplicação | qsoft_governance_v3, migrations até V021 |
| Snapshot | qsoft_v3_snapshot_20261022_095852 |
| Backup original | C:/Users/evelyn.chiaperini/Documents/Codex/backups/Projeto_IV/20261022_095852/projeto_integrado_iv.dump |
| SHA-256 do original | 1EC051ECF93A19C67010C8FE03551C25A75953A0DF11AD453082016453F471B5 |
| Manifesto, CSVs e credenciais privadas | Subdiretório load-v3 do backup original |
| Backups antes de identidades/administração | Diretórios irmãos 20261023_identities e 20261023_admin_separation |
| Ambiente atual | .env.v3.local; .env também aponta para o banco novo |
| Ambiente antigo | .env.legacy.local |
| URLs locais | Backend http://127.0.0.1:8081; frontend http://127.0.0.1:3000 |

Java 21 e PostgreSQL são necessários; Docker é opcional para executar a aplicação
e necessário aos testes Testcontainers. Iniciar backend com
`scripts/run-local.ps1 -EnvFile .env.v3.local` ou
`./scripts/run-local.sh .env.v3.local`; no BFF, selecionar
`QSOFT_ENV_FILE=.env.v3.local` e executar `npm start`.
Conferir DB_HOST/PORT/NAME, SERVER_PORT, API_PROXY_TARGET e SPRING_API_URL.
Arquivo de ambiente explicitamente escolhido e inexistente bloqueia o BFF.
No Windows, executar uma cópia do JAR para não bloquear o repackage do Maven.

Configurações privadas, backups, CSVs e hashes ficam fora do Git. Preservar origem,
snapshot e manifesto. Falha durante carga reverte a transação; falha após commit
exige reconciliação antes de retomar. Não fazer append/TRUNCATE automático nem
apagar tenants provisionados. Para desfazer a carga, restaurar backup apropriado
do banco novo com serviços parados. Voltar à conexão antiga não corrige seu schema.

## Dataset histórico importado

| Tabela staging → historical | Linhas |
|---|---:|
| companies | 18 |
| departments | 161 |
| employees | 2.356 |
| projects | 18 |
| builds | 12.414 |
| deployments | 1.800 |
| issues | 3.455 |
| pull_requests | 5.124 |
| ai_tools | 15 |
| ai_models | 25 |
| ai_usage_logs | 269.515 |
| ai_billing | 7.025 |
| ai_budgets | 2.254 |
| ai_licenses | 5.605 |
| approved_ai_tools | 105 |
| security_events | 69 |
| training_records | 1.597 |

Referência da carga: 8.310.838 requisições entre junho/2025 e julho/2026;
1.058 funcionários e 23 modelos distintos com uso. As contagens não são constantes
para futuras importações. Catálogos/orçamentos operacionais e
observability_usage_records não foram preenchidos com logs históricos.

As 18 empresas foram provisionadas via RegisterTenant: 18 tenants, 18 admins,
90 papéis e 18 concessões iniciais. company_id tornou-se UUID textual do tenant
nas tabelas empresariais, conforme mapa um-para-um do manifesto. Outros IDs
permaneceram textuais. Catálogos ai_tools/ai_models são globais históricos;
builds/deployments/issues/pull_requests exigem recorte via projetos nas consultas.
employees.active converte True/False em 1/0. Timestamps trocam espaço por T, sem
atribuir fuso. Validar tipos, limites, nulos, referências e NaN/Infinity; preservar moeda.

## Procedimento de carga histórica

CLI `scripts/db/historical-import.js`: Node 24 + psql, sem dependência nova.
Modos `plan`, `provision`, `load`; wrapper `scripts/db/load-historical-staging.ps1`.
O antigo load-historical.ps1 não é o procedimento desta carga.

Parâmetros: --mode, --source-env, --target-env, --work-dir, --batch;
--source-db seleciona o snapshot; provision exige --api-url; escrita exige
--confirm-target igual ao banco alvo. Nesta carga: origem .env.legacy.local,
snapshot acima, destino .env.v3.local, batch BUGFIX-DB-001 e work-dir load-v3.

1. Conferir instâncias e rejeitar origem/destino iguais, inclusive aliases.
2. Usar fonte read-only e snapshot consistente. plan valida as 17 tabelas,
   colunas, conversões, referências e mapeamentos.
3. provision usa a API IAM; chave privada permite retomar cadastro sem duplicar tenant.
4. load exporta colunas explícitas e importa em transação única com advisory lock;
   reconcilia contagens e digests ordenados antes do commit.
5. Lote idêntico retorna ALREADY_LOADED; conteúdo parcial/divergente é rejeitado.
   Manifesto é substituído atomicamente, com tentativas limitadas para locks Windows.

Preservar manifest.json, credentials.key e admin-access.private.json. CSVs têm
SHA-256; digests servem para reconciliação, não autenticação. Não confiar só em
contagens ou no estado do arquivo para reaplicar a carga.

## Identidades, vínculos e papéis

Etapa separada: `scripts/db/import-legacy-identities.js` lê public.iam_users do
snapshot e importa app_user, departamentos e vínculos V020 no banco novo.
Preserva UUIDs de usuário/departamento, emails e status; tenant_id vem do manifesto.
Vínculos: iam_legacy_user_links e department_legacy_links, com FKs apenas no
próprio módulo. Consultas analíticas usam portas públicas, conforme ADR-26.

CLI: --mode plan|load, --source-env .env.legacy.local, --target-env .env.v3.local,
--manifest <manifest.json>, --report <fora do Git ou target>, --psql <psql.exe>.
load exige --confirm-target qsoft_governance_v3. V020 deve estar aplicada;
manifesto precisa estar COMPLETED e identificar exatamente as duas instâncias.
Fazer backup antes da escrita.

Foram importados 2.356 usuários: 2.158 ativos e 198 inativos, com 161 departamentos.
Cargos: 1.518 COLLABORATOR/SELF, 732 LEADER/DEPARTMENT e 106 MANAGER/DEPARTMENT.
O importador não inventa executivos/admins nem altera permissões; usa papéis
existentes e concessões MIGRATION. As contas mockadas posteriores de executivo e
administrador foram criadas pelo fluxo oficial de convite/aceite.

Todos os hashes legados tinham 33 caracteres e eram inválidos. Vulpi autorizou
admin123 nos usuários mockados locais; BCrypt válido foi gerado com a biblioteca
existente e passado por --password-hash-file <arquivo privado ignorado>.
Preservar o mesmo arquivo para reexecuções; novos salts causariam divergência.
Hashes válidos são mantidos; hashes inválidos sem substituto explícito abortam.
Contas inativas continuam inativas. A origem permanece intacta.

A importação exige domínio único por empresa, sem colisão, e reconcilia email_domain
do tenant; bootstrap mantém acesso pelo fallback de slug da ADR-32. Transação e
lock protegem a carga. Contas/departamentos divergentes, email conflitante, vínculo
em outra empresa, papel ausente ou concessão revogada abortam tudo. Reexecução
idêntica não duplica contas nem restaura concessões revogadas. Relatórios contêm
somente contagens; não imprimir senhas ou hashes.

A política atual de administrador/executivo e a V021 estão descritas em
[Dashboard, acesso e administração](DASHBOARD_E_ORCAMENTOS.md) e na emenda ADR-36.
