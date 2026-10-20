package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
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

/** Schema da V019: busca nativa, sem extensão de vetor nem texto de pergunta/resposta. */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class KnowledgeSchemaIT {

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

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void naoHaExtensaoDeVetor() {
        Integer extensions = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname IN ('vector','pgvector')",
            Integer.class);
        assertThat(extensions).isZero();
    }

    @Test
    void chunksUsamTsvectorNativoGerado() {
        List<String> types = jdbcTemplate.query(
            "SELECT udt_name || ':' || is_generated FROM information_schema.columns "
                + "WHERE table_name = 'agentpolicies_chunks' AND column_name = 'search_vector'",
            (rs, rowNum) -> rs.getString(1));
        assertThat(types).containsExactly("tsvector:ALWAYS");
    }

    @Test
    void execucoesNaoGuardamPerguntaNemResposta() {
        Set<String> forbidden = Set.of("prompt", "question", "pergunta", "answer", "resposta",
            "response", "content", "text");
        List<String> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'agentpolicies_executions'", String.class);

        assertThat(columns).contains("request_id", "status", "document_ids", "tools_called");
        assertThat(columns).noneMatch(column -> forbidden.contains(column.toLowerCase(Locale.ROOT)));
    }
}
