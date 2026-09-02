package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.CreateUser;
import br.com.qsoft.governance.iam.application.CreateUserCommand;
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
 * valida a persistência do User dentro do tenant e o isolamento entre tenants.
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
    CreateUser createUser;

    @Autowired
    UserRepository userRepository;

    private Tenant newTenant(String prefix) {
        return registerTenant.register(new RegisterTenantCommand(prefix + "-" + UUID.randomUUID(), prefix));
    }

    @Test
    void shouldPersistUserWithinTenant() {
        Tenant tenant = newTenant("acme");

        User user = createUser.create(new CreateUserCommand(
            tenant.id(), "ana@acme.com", "Ana", HierarchyLevel.MANAGER, null));

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
        createUser.create(new CreateUserCommand(
            tenant.id(), "ana@dup.com", "Ana", HierarchyLevel.MANAGER, null));

        assertThatThrownBy(() -> createUser.create(new CreateUserCommand(
            tenant.id(), "ana@dup.com", "Outra", HierarchyLevel.LEADER, null)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void shouldAllowSameEmailInDifferentTenants() {
        Tenant tenantA = newTenant("at");
        Tenant tenantB = newTenant("bt");

        createUser.create(new CreateUserCommand(
            tenantA.id(), "ana@x.com", "Ana", HierarchyLevel.MANAGER, null));
        User inB = createUser.create(new CreateUserCommand(
            tenantB.id(), "ana@x.com", "Ana", HierarchyLevel.MANAGER, null));

        assertThat(inB.tenantId()).isEqualTo(tenantB.id());
        assertThat(userRepository.findByTenantIdAndEmail(tenantB.id(), "ana@x.com")).isPresent();
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> createUser.create(new CreateUserCommand(
            TenantId.random(),
            "ana@x.com", "Ana", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(TenantNotFoundException.class);
    }
}
