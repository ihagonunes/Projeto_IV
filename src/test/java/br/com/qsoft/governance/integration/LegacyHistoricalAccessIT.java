package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = br.com.qsoft.governance.GovernanceApplication.class)
class LegacyHistoricalAccessIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine")
        .withDatabaseName("qsoft_test").withUsername("qsoft").withPassword("test_only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired TestTenants tenants;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    private String token(RestClient client, String email) {
        return (String) client.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "password", "secret123")).retrieve().body(Map.class).get("token");
    }
    private long requests(RestClient client, String token, String uri) {
        var dashboard = client.get().uri(uri).header("Authorization", "Bearer " + token).retrieve().body(Map.class);
        return ((java.util.List<Map<String, Object>>) dashboard.get("series")).stream()
            .mapToLong(row -> ((Number) row.get("requests")).longValue()).sum();
    }
    private void usage(UUID tenant, String employee, String dept, int count) {
        jdbc.update("INSERT INTO historical.ai_usage_logs(usage_id,company_id,employee_id,department_id,\"timestamp\",request_count) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID().toString(), tenant.toString(), employee, dept, "2026-07-15T12:00:00", count);
    }
    @Test void cookieIdentityLinksCannotBroadenExistingRbacOrTenantScope() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var tenant = tenants.ensureTenant("linked", "Linked", "Admin", "admin@linked.com", "secret123");
        var other = tenants.ensureTenant("foreignlinked", "Foreign", "Admin", "admin@foreignlinked.com", "secret123");
        var admin = tenants.admin(tenant, "admin@linked.com");
        UUID department = UUID.randomUUID();
        jdbc.update("INSERT INTO department_departments(id,tenant_id,name,status,created_at,updated_at) VALUES (?,?,'TI','ACTIVE',now(),now())", department, tenant.id().value());
        jdbc.update("INSERT INTO department_legacy_links VALUES (?,?,'D1')", tenant.id().value(), department);
        var self = tenants.ensureMember(tenant, admin.id(), "self@linked.com", "Self", "secret123", HierarchyLevel.COLLABORATOR, department, "COLLABORATOR");
        var peer = tenants.ensureMember(tenant, admin.id(), "peer@linked.com", "Peer", "secret123", HierarchyLevel.COLLABORATOR, department, "COLLABORATOR");
        var manager = tenants.ensureMember(tenant, admin.id(), "manager@linked.com", "Manager", "secret123", HierarchyLevel.MANAGER, department, "MANAGER");
        jdbc.update("INSERT INTO iam_legacy_user_links VALUES (?,?,'E1'),(?,?,'E2'),(?,?,'E3')",
            tenant.id().value(), self.id().value(), tenant.id().value(), peer.id().value(), tenant.id().value(), manager.id().value());
        usage(tenant.id().value(), "E1", "D1", 3);
        usage(tenant.id().value(), "E2", "D1", 5);
        usage(tenant.id().value(), "E4", "D2", 90);
        usage(other.id().value(), "E1", "D1", 999);
        String base = "/api/v1/observability/metrics/historical";
        String selfToken = token(client, "self@linked.com");
        assertThat(requests(client, selfToken, base + "/me?userId=" + peer.id().value() + "&tenantId=" + other.id().value() + "&employeeId=E2")).isEqualTo(3);
        assertThat(requests(client, selfToken, base)).isEqualTo(3);
        assertThat(client.get().uri(base + "?userId=" + peer.id().value()).header("Authorization", "Bearer " + selfToken)
            .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(403);
        String managerToken = token(client, "manager@linked.com");
        assertThat(requests(client, managerToken, base)).isEqualTo(8);
        assertThat(requests(client, managerToken, base + "?userId=" + peer.id().value())).isEqualTo(5);
        assertThat(client.get().uri(base + "?departmentId=D2").header("Authorization", "Bearer " + managerToken)
            .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(403);
        var executive = tenants.ensureMember(tenant, admin.id(), "exec@linked.com", "Executive", "secret123",
            HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");
        assertThat(requests(client, token(client, executive.email()), base)).isEqualTo(98);
        // Administração permanece autenticada, mas não concede leitura analítica.
        String adminToken = token(client, "admin@linked.com");
        for (String uri : new String[] {base, base + "/me", "/api/v1/observability/metrics/usage",
            "/api/v1/observability/metrics/costs", "/api/v1/observability/metrics/me/usage",
            "/api/v1/observability/metrics/me/costs"}) {
            assertThat(client.get().uri(uri).header("Authorization", "Bearer " + adminToken)
                .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(403);
        }
        assertThat(client.get().uri(base + "/me").header("Authorization", "Bearer " + adminToken)
            .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(403);
    }
}
