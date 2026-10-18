# HANDOFF — Protocolo para o modelo que vai implementar a Sprint 3

Leia este arquivo inteiro antes de qualquer tarefa. Ele vale para **todas** as tarefas `TASK-S3-*`.

## 1. Ordem de leitura (carregue só o necessário)

1. `AGENTS.md` (regras inegociáveis, principalmente §19).
2. `ai/tasks/sprint-03/INDEX.md`: ordem, dependências, aprovações pendentes e numeração de
   migrations.
3. O `TASK-S3-*.md` da tarefa e a `SPEC-*` que ele cita (só as seções citadas).
4. Os ADRs citados no packet (`ai/adr/`).
5. `docs/review/VERIFICACAO_FASE0.md`: só os itens citados (V.., D..). Ele traz os fatos do código
   com arquivo e linha.
6. O código do módulo proprietário e das dependências diretas.

## 2. Antes de escrever código

- [ ] Confirme que todas as dependências do packet estão concluídas (`INDEX.md` §3).
- [ ] Confirme que os ADRs exigidos estão **ACCEPTED** em `ai/decisions.md`. Se não estiverem,
      **pare** e devolva a tarefa citando qual falta.
- [ ] Confirme que o número da migration é o reservado em `INDEX.md` §4.
- [ ] Rode `./scripts/check` e anote a contagem de testes (linha de base).

## 3. Regras de execução

- **Uma tarefa = um incremento revisável.** Não misture tarefas.
- Branch e commit seguem `AGENTS.md` §12, com a chave Jira informada pelo responsável. Exemplo:
  `feat/SPI-46-convite-com-concessao`, commit `feat(SPI-46): convite carrega a concessao`.
- **Só crie branch, commit ou push se o responsável autorizar explicitamente.** O padrão é deixar
  as mudanças locais e entregar o relatório.
- **Teste negativo primeiro**, quando houver: escreva o teste que falha hoje, veja-o falhar e então
  implemente.
- Toda migration com dados tem IT de Flyway com Testcontainers.
- Nunca:
  - adicionar dependência Maven/npm;
  - inserir `RoleAssignment` direto em IT (use `support/TestTenants`);
  - confiar em `tenant_id` vindo do cliente;
  - logar token, senha, segredo, prompt ou resposta;
  - alterar o `Status` de ADR;
  - mexer no componente de chat do front (SPI-136, da Lyn);
  - mudar a allowlist de módulos;
  - desligar teste para "passar".
- Toda query nativa tenant-aware tem `tenant_id = ?` explícito.
- A spec é a referência. Se o código real divergir da spec, **o repositório vence**: implemente a
  menor adaptação segura e registre a divergência no relatório. Nunca enfraqueça uma regra de
  segurança para caber.

## 4. Condições de parada (pare e pergunte ao responsável)

- ADR exigido não está ACCEPTED, ou falta uma decisão marcada como pendente no `INDEX.md` §2.
- A implementação exigiria dependência nova, endpoint fora da lista do `INDEX.md` §5, migration
  além da reservada, evento novo ou mudança de allowlist.
- Conflito entre a spec e um invariante de segurança do `AGENTS.md`.
- Necessidade de tocar arquivos de outra trilha em andamento (`INDEX.md` §3).

## 5. Teste de aceite ponta a ponta da Onda A

Entra no PR da A5 (ou da A4, se as duas forem juntas). Escrito durante o planejamento: hoje ele
**falha** com 401 no login do registrante, porque `POST /tenants` não cria usuário.

Caminho: `src/test/java/br/com/qsoft/governance/iam/infrastructure/OnboardingFlowIT.java`

```java
package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fluxo de produção ponta a ponta, SEM seed de demonstração e SEM inserir
 * {@code RoleAssignment} à mão (ADR-37, AZ01/AZ02/AZ03): registrar tenant →
 * o registrante vira TENANT_ADMIN → convida → convidado aceita → login →
 * acessa somente o que a concessão permite. Exige Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class,
    properties = "app.bootstrap.demo-data=false")
class OnboardingFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.6-alpine")
            .withDatabaseName("qsoft_test")
            .withUsername("qsoft")
            .withPassword("qsoft_test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String ADMIN_PASSWORD = "admin-secret-123";
    private static final String MEMBER_PASSWORD = "member-secret-123";

    @LocalServerPort
    int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void registrantBecomesAdminInvitesCollaboratorAndCollaboratorSeesOnlyWhatTheGrantAllows() {
        String adminEmail = "dona@onboarding.com";
        registerTenant("onboarding", "onboarding.com", adminEmail);

        // AZ01: o registrante loga e administra o tenant.
        String adminToken = login(adminEmail, ADMIN_PASSWORD);
        assertThat(status("PUT", "/api/v1/tenants/me", adminToken, Map.of("name", "Onboarding SA")))
            .isEqualTo(200);

        // AZ02: o admin convida um COLLABORATOR; o convidado aceita e loga.
        String memberEmail = "colab@onboarding.com";
        Map<?, ?> invite = client.post().uri("/api/v1/convites")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", memberEmail, "roleKey", "COLLABORATOR"))
            .retrieve().body(Map.class);
        assertThat(invite).isNotNull();
        String inviteToken = (String) invite.get("token");
        assertThat(status("POST", "/api/v1/convites/aceitar", null,
            Map.of("token", inviteToken, "name", "Colab", "password", MEMBER_PASSWORD)))
            .isEqualTo(200);
        String memberToken = login(memberEmail, MEMBER_PASSWORD);

        // AZ03: o colaborador lê o próprio uso, mas não lista usuários nem administra o tenant.
        assertThat(status("GET", "/api/v1/observability/metrics/usage", memberToken, null))
            .isEqualTo(200);
        assertThat(status("GET", "/api/v1/usuarios", memberToken, null)).isEqualTo(403);
        assertThat(status("PUT", "/api/v1/tenants/me", memberToken, Map.of("name", "Hack")))
            .isEqualTo(403);
    }

    private void registerTenant(String slug, String domain, String adminEmail) {
        client.post().uri("/api/v1/tenants")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "slug", slug,
                "name", "Onboarding",
                "emailDomain", domain,
                "adminName", "Dona",
                "adminEmail", adminEmail,
                "adminPassword", ADMIN_PASSWORD))
            .retrieve().toBodilessEntity();
    }

    private String login(String email, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", password))
            .retrieve().body(Map.class);
        assertThat(response).isNotNull();
        return (String) response.get("token");
    }

    private int status(String method, String uri, String token, Object body) {
        RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method))
            .uri(uri);
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return spec.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value();
        }
    }
}
```

## 6. Definition of Done (por tarefa)

- [ ] Todos os critérios do packet atendidos, ou o motivo de cada um não atendido.
- [ ] `./scripts/check --changed`, `./scripts/check` e, se houver persistência ou multi-tenancy,
      `./scripts/check-integration` verdes.
- [ ] ArchUnit verde.
- [ ] `git diff` revisado: só arquivos da tarefa; nenhum segredo; nenhum `.env`.
- [ ] Documentação canônica tocada quando o packet pede (`ai/contracts.md` e afins).

## 7. Relatório de entrega (copie e preencha)

```md
### TASK-S3-xx — <título>
- Jira: SPI-..
- Arquivos alterados: <lista>
- Migrations: <versão ou "nenhuma">
- Testes adicionados: <IDs AZ/DB/TR + nomes>
- Suíte: unit <n> ✅/❌ · integração <n> ✅/❌ (linha de base: <n>)
- Critérios do packet: <cada um ✅/❌ com motivo>
- Divergências spec × código: <lista ou "nenhuma">
- Riscos residuais: <lista>
- Pendências para outra tarefa: <lista>
- Ação sugerida no Jira: <fechar / manter / redefinir>
- O que NÃO verifiquei: <lista explícita>
```
