package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TR11 (ADR-38): a persistência das tentativas só guarda metadados. Nenhuma
 * coluna pode carregar conteúdo, prompt, resposta ou segredo.
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class RequestAttemptSchemaIT {

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

    private static final Set<String> FORBIDDEN = Set.of(
        "content", "prompt", "response", "secret", "credential", "body", "messages");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void tentativasNaoGuardamConteudoNemSegredo() {
        List<String> columns = columns("aigateway_request_attempts");

        assertThat(columns).isNotEmpty();
        assertThat(columns).contains("request_id", "tenant_id", "authorization_status",
            "execution_status", "delivery_status", "cost_status", "estimated_cost",
            "unit_price_input", "unit_price_output");
        assertThat(columns)
            .noneMatch(column -> FORBIDDEN.contains(column.toLowerCase(Locale.ROOT)));
    }

    @Test
    void providerGuardaApenasReferenciaDaCredencial() {
        List<String> columns = columns("aigateway_providers");

        assertThat(columns).contains("kind", "base_url", "credential_ref", "timeout_ms");
        assertThat(columns)
            .noneMatch(column -> FORBIDDEN.contains(column.toLowerCase(Locale.ROOT)));
    }

    @Test
    void modelGuardaExternalName() {
        assertThat(columns("aigateway_models")).contains("external_name");
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList("""
            SELECT column_name FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = ?
            """, String.class, table);
    }
}
