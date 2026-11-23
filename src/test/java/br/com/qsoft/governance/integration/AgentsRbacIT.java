package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RBAC de agentes (V028/SystemRole) exercitado pela mesma {@link AuthorizationQuery}
 * usada pelos endpoints: decisão negada equivale a 403 e recurso de outro tenant
 * a 404 (ver {@code AgentsPersistenceIT}). Concessões são criadas pelos casos de
 * uso de produção ({@link TestTenants}), nunca por insert direto.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsRbacIT {

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

    private static final String SLUG = "agentsrbac";
    private static final String OTHER_SLUG = "agentsrbacother";
    private static final String ADMIN_EMAIL = "admin@agentsrbac.com";
    private static final String PASSWORD = "secret123";
    private static final UUID DEPARTMENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_DEPARTMENT =
        UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    AuthorizationQuery authorizationQuery;

    @Autowired
    TestTenants testTenants;

    private Tenant tenant;
    private User admin;
    private User collaborator;
    private User leader;
    private User manager;
    private User executive;

    @BeforeEach
    void setUp() {
        tenant = testTenants.ensureTenant(SLUG, "Agents RBAC", "Admin", ADMIN_EMAIL, PASSWORD);
        UserId adminId = testTenants.admin(tenant, ADMIN_EMAIL).id();
        admin = testTenants.admin(tenant, ADMIN_EMAIL);
        collaborator = testTenants.ensureMember(tenant, adminId, "collab@agentsrbac.com",
            "Collab", PASSWORD, HierarchyLevel.COLLABORATOR, null, "COLLABORATOR");
        leader = testTenants.ensureMember(tenant, adminId, "leader@agentsrbac.com",
            "Leader", PASSWORD, HierarchyLevel.LEADER, DEPARTMENT, "LEADER");
        manager = testTenants.ensureMember(tenant, adminId, "manager@agentsrbac.com",
            "Manager", PASSWORD, HierarchyLevel.MANAGER, DEPARTMENT, "MANAGER");
        executive = testTenants.ensureMember(tenant, adminId, "exec@agentsrbac.com",
            "Exec", PASSWORD, HierarchyLevel.EXECUTIVE, null, "EXECUTIVE");

        testTenants.ensureTenant(OTHER_SLUG, "Agents RBAC Other", "Admin Other",
            "admin@agentsrbacother.com", PASSWORD);
    }

    @Test
    void collaboratorReadsAndRunsOwnFlowsButCannotManage() {
        UserId user = collaborator.id();
        AccessTarget own = new AccessTarget.OwnedTarget(user, null);
        AccessTarget other = new AccessTarget.OwnedTarget(leader.id(), DEPARTMENT);

        assertThat(allowed(user, Action.READ, own)).isTrue();
        assertThat(allowed(user, Action.RUN, own)).isTrue();
        assertThat(allowed(user, Action.MANAGE, own)).isFalse();
        assertThat(allowed(user, Action.READ, other)).isFalse();
    }

    @Test
    void leaderManagesOnlyItsDepartment() {
        UserId user = leader.id();

        assertThat(allowed(user, Action.READ, new AccessTarget.DepartmentTarget(DEPARTMENT)))
            .isTrue();
        assertThat(allowed(user, Action.MANAGE, new AccessTarget.DepartmentTarget(DEPARTMENT)))
            .isTrue();
        assertThat(allowed(user, Action.RUN, new AccessTarget.DepartmentTarget(DEPARTMENT)))
            .isTrue();
        assertThat(allowed(user, Action.MANAGE, new AccessTarget.DepartmentTarget(OTHER_DEPARTMENT)))
            .isFalse();
    }

    @Test
    void managerManagesItsDepartmentWithoutTenantWideScope() {
        UserId user = manager.id();

        assertThat(allowed(user, Action.MANAGE, new AccessTarget.DepartmentTarget(DEPARTMENT)))
            .isTrue();
        assertThat(allowed(user, Action.MANAGE, new AccessTarget.TenantTarget())).isFalse();
    }

    @Test
    void executiveReadsAndRunsTenantButCannotManage() {
        UserId user = executive.id();
        AccessTarget tenantTarget = new AccessTarget.TenantTarget();

        assertThat(allowed(user, Action.READ, tenantTarget)).isTrue();
        assertThat(allowed(user, Action.RUN, tenantTarget)).isTrue();
        assertThat(allowed(user, Action.MANAGE, tenantTarget)).isFalse();
    }

    @Test
    void tenantAdminReadsManagesAndRunsTenant() {
        UserId user = admin.id();
        AccessTarget tenantTarget = new AccessTarget.TenantTarget();

        assertThat(allowed(user, Action.READ, tenantTarget)).isTrue();
        assertThat(allowed(user, Action.MANAGE, tenantTarget)).isTrue();
        assertThat(allowed(user, Action.RUN, tenantTarget)).isTrue();
    }

    @Test
    void actorNeverActsOnAnotherTenant() {
        Tenant other = testTenants.ensureTenant(OTHER_SLUG, "Agents RBAC Other",
            "Admin Other", "admin@agentsrbacother.com", PASSWORD);

        assertThat(allowed(other.id(), collaborator.id(), Action.READ,
            new AccessTarget.OwnedTarget(collaborator.id(), null))).isFalse();
        assertThat(allowed(other.id(), admin.id(), Action.MANAGE,
            new AccessTarget.TenantTarget())).isFalse();
    }

    private boolean allowed(UserId user, Action action, AccessTarget target) {
        return allowed(tenant.id(), user, action, target);
    }

    private boolean allowed(TenantId tenantId, UserId user, Action action, AccessTarget target) {
        TenantContextHolder.set(new TenantContext(tenantId, user));
        try {
            return authorizationQuery.isAllowed(tenantId, user, Resource.AGENT, action, target);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
