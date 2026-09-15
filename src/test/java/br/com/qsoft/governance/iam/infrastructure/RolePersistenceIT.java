package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Resource;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
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
    RoleRepository roleRepository;

    private Tenant newTenant() {
        return registerTenant.register(new RegisterTenantCommand(
            "rbac-" + UUID.randomUUID(), "RBAC Demo"));
    }

    @Test
    void shouldPersistRoleWithPermissions() {
        Tenant tenant = newTenant();
        Role role = Role.create(RoleId.random(), tenant.id(), "ADMIN");
        role.grantPermission(Permission.of(Resource.USER, Action.MANAGE));
        role.grantPermission(Permission.of(Resource.TENANT, Action.READ));

        Role saved = roleRepository.save(role);
        Role found = roleRepository.findById(saved.id()).orElseThrow();

        assertThat(found.name()).isEqualTo("ADMIN");
        assertThat(found.tenantId()).isEqualTo(tenant.id());
        assertThat(found.permissions()).containsExactlyInAnyOrder(
            Permission.of(Resource.USER, Action.MANAGE),
            Permission.of(Resource.TENANT, Action.READ));
    }

    @Test
    void shouldDetectDuplicateNameWithinTenant() {
        Tenant tenant = newTenant();
        roleRepository.save(Role.create(RoleId.random(), tenant.id(), "MANAGER"));

        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "MANAGER")).isTrue();
        assertThat(roleRepository.existsByTenantIdAndName(tenant.id(), "OUTRO")).isFalse();
    }
}
