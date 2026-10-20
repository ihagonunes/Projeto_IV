package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.Set;
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
 * Teste de integração com PostgreSQL real (Testcontainers) + Flyway (V001/V002):
 * persistência de usuário, unicidade por tenant e leitura tenant-scoped.
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class UserPersistenceIT {

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
    ProvisionUser provisionUser;

    @Autowired
    UserRepository userRepository;

    private Tenant newTenant(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID();
        return registerTenant.register(new RegisterTenantCommand(
            slug, prefix, slug + ".example", "Admin", "admin@" + slug + ".example", "secret123"));
    }

    private User provision(Tenant tenant, String email, String name, HierarchyLevel level) {
        return provisionUser.provision(new ProvisionUserCommand(
            tenant.id(), email, name, level, "secret123", null));
    }

    @Test
    void shouldPersistUserWithinTenant() {
        Tenant tenant = newTenant("acme");

        User user = provision(tenant, "ana@acme.com", "Ana", HierarchyLevel.MANAGER);

        assertThat(user.id()).isNotNull();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);

        User found = userRepository.findByTenantIdAndEmail(tenant.id(), "ana@acme.com").orElseThrow();
        assertThat(found.id()).isEqualTo(user.id());
        assertThat(found.tenantId()).isEqualTo(tenant.id());
        assertThat(found.email()).isEqualTo("ana@acme.com");
        assertThat(found.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
    }

    @Test
    void shouldRejectDuplicateEmailInSameTenant() {
        Tenant tenant = newTenant("dup");
        provision(tenant, "ana@dup.com", "Ana", HierarchyLevel.MANAGER);

        assertThatThrownBy(() -> provision(tenant, "ana@dup.com", "Outra", HierarchyLevel.LEADER))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void shouldAllowSameEmailInDifferentTenants() {
        Tenant tenantA = newTenant("at");
        Tenant tenantB = newTenant("bt");

        provision(tenantA, "ana@x.com", "Ana", HierarchyLevel.MANAGER);
        User inB = provision(tenantB, "ana@x.com", "Ana", HierarchyLevel.MANAGER);

        assertThat(inB.tenantId()).isEqualTo(tenantB.id());
        assertThat(userRepository.findByTenantIdAndEmail(tenantB.id(), "ana@x.com")).isPresent();
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> provisionUser.provision(new ProvisionUserCommand(
            TenantId.random(), "ana@x.com", "Ana", HierarchyLevel.MANAGER,
            "secret123", null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldPersistHierarchyLevelChange() {
        Tenant tenant = newTenant("hier");
        User user = provision(tenant, "ana@hier.com", "Ana", HierarchyLevel.COLLABORATOR);

        user.changeHierarchyLevel(HierarchyLevel.EXECUTIVE);
        userRepository.save(user);

        User reloaded = userRepository.findByIdUnscoped(user.id()).orElseThrow();
        assertThat(reloaded.hierarchyLevel()).isEqualTo(HierarchyLevel.EXECUTIVE);
    }

    @Test
    void shouldNotReadOtherTenantUserWhenContextIsSet() {
        Tenant tenantA = newTenant("isoA");
        Tenant tenantB = newTenant("isoB");
        User userA = provision(tenantA, "ana@iso.com", "Ana", HierarchyLevel.MANAGER);
        User userB = provision(tenantB, "bob@iso.com", "Bob", HierarchyLevel.MANAGER);

        TenantContextHolder.set(new TenantContext(tenantA.id(), UserId.random()));
        try {
            assertThat(userRepository.findById(userA.id())).isPresent();
            assertThat(userRepository.findById(userB.id())).isEmpty();
            assertThat(userRepository.findByTenantIdAndEmail(tenantB.id(), "bob@iso.com")).isEmpty();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
