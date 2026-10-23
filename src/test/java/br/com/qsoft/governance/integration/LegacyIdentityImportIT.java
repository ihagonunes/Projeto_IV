package br.com.qsoft.governance.integration;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Executa o carregador Node real contra dois bancos PostgreSQL descartáveis. */
@Testcontainers
class LegacyIdentityImportIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine")
        .withDatabaseName("qsoft_loader_control").withUsername("qsoft").withPassword("loader_test_only");

    @TempDir
    Path work;

    @Test
    void identityImportMustPreserveIsolationAtomicityAndRetrySafety() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE qsoft_loader_source");
            statement.execute("CREATE DATABASE qsoft_loader_destination");
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/qsoft_loader_destination";
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").load().migrate();
        ProcessBuilder process = new ProcessBuilder("node", "--test", "src/test/db/legacy-identities.integration.test.js");
        process.redirectErrorStream(true);
        Path output = work.resolve("node-test.log");
        process.redirectOutput(output.toFile());
        process.environment().put("QSOFT_LOADER_TEST_CONFIG", new ObjectMapper().writeValueAsString(Map.of(
            "host", POSTGRES.getHost(), "port", POSTGRES.getMappedPort(5432).toString(),
            "user", POSTGRES.getUsername(), "password", POSTGRES.getPassword(), "workDir", work.toString())));
        Process running = process.start();
        boolean completed = running.waitFor(240, TimeUnit.SECONDS);
        if (!completed) running.destroyForcibly();
        assertThat(completed).as("O teste Node do loader deve terminar em 240s").isTrue();
        String report = java.nio.file.Files.readString(output);
        assertThat(running.exitValue()).withFailMessage(report).isZero();
        System.out.println(report);
    }
}
