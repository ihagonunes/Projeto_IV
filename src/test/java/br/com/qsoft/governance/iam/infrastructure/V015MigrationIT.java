package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IT de Flyway da V015 (ADR-36/ADR-37): migra até V14, insere dados legados,
 * migra até V15 e verifica o backfill (AZ18a/AZ18b/AZ18c) e a FK composta que
 * rejeita papel de outro tenant (parte de banco de AZ09). Exige Docker.
 */
@Testcontainers
class V015MigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.6-alpine")
            .withDatabaseName("qsoft_test")
            .withUsername("qsoft")
            .withPassword("qsoft_test");

    private static final List<String> LEGACY_ADMIN_PERMISSIONS = List.of(
        "MANAGE_TENANT", "MANAGE_USERS", "MANAGE_HIERARCHY", "MANAGE_DEPARTMENTS",
        "MANAGE_MODEL_POLICIES", "MANAGE_AI_CATALOG", "MANAGE_KNOWLEDGE", "MANAGE_LICENSES",
        "MANAGE_TRAINING");

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("public", "historical")
            .cleanDisabled(false)
            .target(target)
            .load();
    }

    private void resetToV14() {
        flyway("14").clean();
        flyway("14").migrate();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void exec(String sql, Object... args) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private UUID insertTenant(String suffix) throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO iam_tenants (id, slug, name, status, email_domain) VALUES (?,?,?,?,?)",
            id, "tenant-" + suffix, "Tenant " + suffix, "ACTIVE", suffix + ".com");
        return id;
    }

    private UUID insertUser(UUID tenantId, String email) throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO app_user (id, tenant_id, email, name, hierarchy_level, department_id, "
                + "status, created_at, updated_at) VALUES (?,?,?,?,?,?,?, now(), now())",
            id, tenantId, email, "User", "EXECUTIVE", null, "ACTIVE");
        return id;
    }

    private String queryString(String sql, Object... args) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int queryInt(String sql, Object... args) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Test
    void az18aLegacyAdminBecomesTenantAdminAndExecutiveIsTenantScoped() throws Exception {
        resetToV14();
        UUID tenantId = insertTenant("az18a");
        UUID userId = insertUser(tenantId, "admin@az18a.com");
        UUID executiveRoleId = UUID.randomUUID();
        exec("INSERT INTO iam_roles (id, tenant_id, name, created_at, updated_at) "
            + "VALUES (?,?,?, now(), now())", executiveRoleId, tenantId, "EXECUTIVE");
        exec("INSERT INTO iam_role_assignments (id, tenant_id, user_id, role_id, department_id) "
            + "VALUES (?,?,?,?, null)", UUID.randomUUID(), tenantId, userId, executiveRoleId);
        for (String permission : LEGACY_ADMIN_PERMISSIONS) {
            exec("INSERT INTO user_permission (user_id, permission) VALUES (?,?)", userId, permission);
        }

        flyway("latest").migrate();

        assertThat(queryString("SELECT a.scope_type FROM iam_role_assignments a "
            + "JOIN iam_roles r ON r.id = a.role_id "
            + "WHERE a.user_id = ? AND r.name = 'EXECUTIVE'", userId)).isEqualTo("TENANT");

        assertThat(queryInt("SELECT count(*) FROM iam_role_assignments a "
            + "JOIN iam_roles r ON r.id = a.role_id "
            + "WHERE a.user_id = ? AND r.name = 'TENANT_ADMIN' AND a.origin = 'MIGRATION'",
            userId)).isEqualTo(1);
    }

    @Test
    void az18bMigrationAbortsOnPartialLegacyPermissions() throws Exception {
        resetToV14();
        UUID tenantId = insertTenant("az18b");
        UUID userId = insertUser(tenantId, "partial@az18b.com");
        exec("INSERT INTO user_permission (user_id, permission) VALUES (?,?)",
            userId, "MANAGE_TENANT");
        exec("INSERT INTO user_permission (user_id, permission) VALUES (?,?)",
            userId, "MANAGE_USERS");

        assertThatThrownBy(() -> flyway("latest").migrate()).isInstanceOf(FlywayException.class);
    }

    @Test
    void az18cPersistedPermissionsMatchSystemRole() throws Exception {
        resetToV14();
        insertTenant("az18c");

        flyway("latest").migrate();

        Map<String, Set<String>> persisted = new HashMap<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT r.name, p.resource, p.action FROM iam_role_permissions p "
                     + "JOIN iam_roles r ON r.id = p.role_id");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                persisted.computeIfAbsent(rs.getString(1), key -> new HashSet<>())
                    .add(rs.getString(2) + ":" + rs.getString(3));
            }
        }

        for (SystemRole role : SystemRole.values()) {
            Set<String> expected = role.permissions().stream()
                .map(permission -> permission.resource().name() + ":" + permission.action().name())
                .collect(Collectors.toSet());
            assertThat(persisted.get(role.name()))
                .as("permissões persistidas de %s", role.name())
                .isEqualTo(expected);
        }
    }

    @Test
    void az09CompositeForeignKeyRejectsRoleFromAnotherTenant() throws Exception {
        resetToV14();
        UUID tenantA = insertTenant("az09a");
        UUID tenantB = insertTenant("az09b");

        flyway("latest").migrate();

        UUID userA = insertUser(tenantA, "a@az09.com");
        UUID roleFromB = UUID.fromString(queryString(
            "SELECT id FROM iam_roles WHERE tenant_id = ? AND name = 'COLLABORATOR'", tenantB));

        assertThatThrownBy(() -> exec(
            "INSERT INTO iam_role_assignments "
                + "(id, tenant_id, user_id, role_id, scope_type, origin, granted_at) "
                + "VALUES (?,?,?,?,?,?, now())",
            UUID.randomUUID(), tenantA, userA, roleFromB, "SELF", "MIGRATION"))
            .isInstanceOf(SQLException.class);
    }
}
