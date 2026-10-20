package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.AuthenticateUser;
import br.com.qsoft.governance.iam.application.AuthenticatedUser;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.LoginCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidCredentialsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração do login (Tenant + User + BCrypt) com PostgreSQL real.
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class AuthenticationIT {

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
    AuthenticateUser authenticateUser;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Tenant tenant(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID();
        return registerTenant.register(new RegisterTenantCommand(
            slug, prefix, slug + ".example", "Admin", "admin@" + slug + ".example", "secret123"));
    }

    @Test
    void shouldLoginWithValidCredentials() {
        Tenant tenant = tenant("auth");
        User user = provisionUser.provision(new ProvisionUserCommand(tenant.id(), "ana@auth.com", "Ana", HierarchyLevel.MANAGER, "secret123", null));

        AuthenticatedUser result =
            authenticateUser.login(new LoginCommand(tenant.slug(), "ana@auth.com", "secret123"));

        assertThat(result.userId()).isEqualTo(user.id());
        assertThat(result.tenantId()).isEqualTo(tenant.id());
        assertThat(result.email()).isEqualTo("ana@auth.com");
    }

    @Test
    void shouldRejectWrongPassword() {
        Tenant tenant = tenant("auth");
        User user = provisionUser.provision(new ProvisionUserCommand(tenant.id(), "bob@auth.com", "Bob", HierarchyLevel.COLLABORATOR, "secret123", null));

        assertThatThrownBy(() -> authenticateUser.login(new LoginCommand(tenant.slug(), "bob@auth.com", "errada")))
            .isInstanceOf(InvalidCredentialsException.class);
    }
}
