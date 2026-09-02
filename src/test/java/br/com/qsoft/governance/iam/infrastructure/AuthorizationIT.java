package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevelCommand;
import br.com.qsoft.governance.iam.application.CreateUser;
import br.com.qsoft.governance.iam.application.CreateUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
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
 * Teste de integração do RBAC (AuthorizationQuery + ChangeUserHierarchyLevel)
 * com PostgreSQL real. Exige Docker.
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class AuthorizationIT {

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
    ChangeUserHierarchyLevel changeUserHierarchyLevel;

    @Autowired
    AuthorizationQuery authorizationQuery;

    @Autowired
    UserRepository userRepository;

    private Tenant tenant(String prefix) {
        return registerTenant.register(new RegisterTenantCommand(prefix + "-" + UUID.randomUUID(), prefix));
    }

    private User newUser(Tenant tenant, String email) {
        User user = createUser.create(new CreateUserCommand(
            tenant.id(), email, email, HierarchyLevel.COLLABORATOR, null));
        return userRepository.findById(user.id()).orElseThrow();
    }

    @Test
    void shouldAllowAdminPermissionToChangeHierarchy() {
        Tenant tenant = tenant("rbac");
        User admin = newUser(tenant, "admin@rbac.com");
        admin.grantPermission(Permission.MANAGE_HIERARCHY);
        userRepository.save(admin);
        User target = newUser(tenant, "ana@rbac.com");

        AuthorizationResult result =
            authorizationQuery.checkPermission(admin.id(), Permission.MANAGE_HIERARCHY);
        assertThat(result.allowed()).isTrue();

        User updated = changeUserHierarchyLevel.change(
            new ChangeUserHierarchyLevelCommand(admin.id(), target.id(), HierarchyLevel.EXECUTIVE));
        assertThat(updated.hierarchyLevel()).isEqualTo(HierarchyLevel.EXECUTIVE);
    }

    @Test
    void shouldDenyCollaboratorToChangeHierarchy() {
        Tenant tenant = tenant("rbac");
        User collaborator = newUser(tenant, "user@rbac.com");
        User target = newUser(tenant, "ana@rbac.com");

        assertThatThrownBy(() -> changeUserHierarchyLevel.change(
            new ChangeUserHierarchyLevelCommand(collaborator.id(), target.id(), HierarchyLevel.EXECUTIVE)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }
}
