package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.CreateUser;
import br.com.qsoft.governance.iam.application.CreateUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração do modelo RBAC (SPI-39) com PostgreSQL real + Flyway (V005).
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class RolePersistenceIT {

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
    RegisterTenant registerTenant;

    @Autowired
    CreateUser createUser;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    private Tenant newTenant() {
        String slug = "rbac-" + UUID.randomUUID();
        return registerTenant.register(new RegisterTenantCommand(slug, "RBAC Demo", slug + ".example"));
    }

    @Test
    void shouldPersistRoleWithPermissions() {
        Tenant tenant = newTenant();
        // A V015 restringe iam_roles aos papéis de sistema; reusa um deles.
        Role role = roleRepository.findByTenantIdAndName(tenant.id(), "MANAGER").orElseThrow();
        role.grantPermission(Permission.of(Resource.TENANT, Action.READ));

        Role saved = roleRepository.save(role);
        Role found = roleRepository.findById(saved.id()).orElseThrow();

        assertThat(found.name()).isEqualTo("MANAGER");
        assertThat(found.tenantId()).isEqualTo(tenant.id());
        assertThat(found.permissions()).contains(
            Permission.of(Resource.USER, Action.MANAGE),
            Permission.of(Resource.TENANT, Action.READ));
    }

    @Test
    void shouldDetectExistingSystemRoleNamesWithinTenant() {
        Tenant tenant = newTenant();

        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "COLLABORATOR")).isTrue();
        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "NAO_EXISTE")).isFalse();
    }

    @Test
    void shouldProvisionDefaultRolesOnTenantRegistration() {
        Tenant tenant = newTenant();

        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "COLLABORATOR")).isTrue();
        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "LEADER")).isTrue();
        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "MANAGER")).isTrue();
        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "EXECUTIVE")).isTrue();
        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "TENANT_ADMIN")).isTrue();
    }

    @Test
    void shouldPersistRoleAssignment() {
        Tenant tenant = newTenant();
        User user = createUser.create(new CreateUserCommand(
            tenant.id(), "assign@rbac.com", "Assign", HierarchyLevel.MANAGER, null));
        Role role = roleRepository.findByTenantIdAndName(tenant.id(), "EXECUTIVE").orElseThrow();

        RoleAssignment saved = roleAssignmentRepository.save(RoleAssignment.grant(
            user.id(), role, null,
            br.com.qsoft.governance.iam.domain.rbac.GrantOrigin.MIGRATION, null,
            java.time.Instant.now()));

        assertThat(roleAssignmentRepository.findById(saved.id())).isPresent();
        assertThat(roleAssignmentRepository.findByTenantIdAndUserId(tenant.id(), user.id()))
            .hasSize(1);
        assertThat(roleAssignmentRepository.findActiveByTenantIdAndUserId(tenant.id(), user.id()))
            .hasSize(1);
    }
}
