package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** Exercises the installed executable JAR, the real setup CLI and disposable databases. */
@Testcontainers
class LocalSetupIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine")
        .withDatabaseName("setup_control").withUsername("qsoft").withPassword("setup_test_only");
    @TempDir Path work;

    @Test void teamSetupIsRepeatableAndPreservesSourceAndRevocations() throws Exception {
        var process = new ProcessBuilder("node", "--test", "src/test/db/setup-local.integration.test.js");
        process.redirectErrorStream(true);
        Path output = work.resolve("setup-test.log");
        process.redirectOutput(output.toFile());
        process.environment().put("QSOFT_LOADER_TEST_CONFIG", new ObjectMapper().writeValueAsString(Map.of(
            "host", POSTGRES.getHost(), "port", POSTGRES.getMappedPort(5432).toString(),
            "user", POSTGRES.getUsername(), "password", POSTGRES.getPassword(), "workDir", work.toString(),
            "useDump", Boolean.parseBoolean(System.getenv("QSOFT_SETUP_TEST_DUMP")))));
        Process running = process.start();
        boolean completed = running.waitFor(900, TimeUnit.SECONDS);
        if (!completed) running.destroyForcibly();
        assertThat(completed).as("Setup completo deve terminar em 900s").isTrue();
        assertThat(running.exitValue()).withFailMessage(Files.readString(output)).isZero();
        System.out.println(Files.readString(output));
    }
}
