package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidCredentialsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticateUserTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private final Map<String, Tenant> tenantsBySlug = new LinkedHashMap<>();
    private final Map<String, User> usersByTenantAndEmail = new LinkedHashMap<>();
    private final Map<UserId, User> usersById = new LinkedHashMap<>();

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant tenant) {
            tenantsBySlug.put(tenant.slug(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return tenantsBySlug.values().stream().filter(t -> t.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return Optional.ofNullable(tenantsBySlug.get(slug));
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenantsBySlug.containsKey(slug);
        }
    };

    private final UserRepository userRepository = new UserRepository() {
        @Override
        public User save(User user) {
            usersByTenantAndEmail.put(user.tenantId() + "|" + user.email(), user);
            usersById.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return Optional.ofNullable(usersByTenantAndEmail.get(tenantId + "|" + email));
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersByTenantAndEmail.containsKey(tenantId + "|" + email);
        }
    };

    private final AuthenticateUser useCase = new AuthenticateUser(tenantRepository, userRepository, encoder);

    private Tenant activeTenant(String slug) {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(id, slug, slug, TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsBySlug.put(slug, tenant);
        return tenant;
    }

    private User activeUser(Tenant tenant, String email, String rawPassword) {
        User user = User.create(UserId.random(), tenant.id(), email, email, HierarchyLevel.MANAGER, null);
        user.setPasswordHash(encoder.encode(rawPassword));
        usersByTenantAndEmail.put(tenant.id() + "|" + email, user);
        usersById.put(user.id(), user);
        return user;
    }

    @Test
    void shouldAuthenticateValidCredentials() {
        Tenant tenant = activeTenant("qsoft");
        User user = activeUser(tenant, "ana@qsoft.com", "secret123");

        AuthenticatedUser result = useCase.login(new LoginCommand("qsoft", "ana@qsoft.com", "secret123"));

        assertThat(result.userId()).isEqualTo(user.id());
        assertThat(result.tenantId()).isEqualTo(tenant.id());
        assertThat(result.email()).isEqualTo("ana@qsoft.com");
        assertThat(result.hierarchyLevel()).isEqualTo("MANAGER");
    }

    @Test
    void shouldDeriveTenantFromEmailDomainWhenSlugAbsent() {
        Tenant tenant = activeTenant("qsoft");
        activeUser(tenant, "ana@qsoft.com", "secret123");

        AuthenticatedUser result = useCase.login(new LoginCommand(null, "ana@qsoft.com", "secret123"));

        assertThat(result.tenantId()).isEqualTo(tenant.id());
        assertThat(result.email()).isEqualTo("ana@qsoft.com");
    }

    @Test
    void shouldRejectWrongPassword() {
        Tenant tenant = activeTenant("qsoft");
        activeUser(tenant, "ana@qsoft.com", "secret123");

        assertThatThrownBy(() -> useCase.login(new LoginCommand("qsoft", "ana@qsoft.com", "errada")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> useCase.login(new LoginCommand("nao-existe", "a@b.com", "x")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectInactiveTenant() {
        TenantId id = TenantId.random();
        tenantsBySlug.put("inactive", Tenant.restore(id, "inactive", "Inactivo",
            TenantStatus.INACTIVE, Instant.now(), Instant.now()));

        assertThatThrownBy(() -> useCase.login(new LoginCommand("inactive", "a@b.com", "x")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectInactiveUser() {
        Tenant tenant = activeTenant("qsoft");
        User user = activeUser(tenant, "ana@qsoft.com", "secret123");
        user.deactivate();
        usersById.put(user.id(), user);

        assertThatThrownBy(() -> useCase.login(new LoginCommand("qsoft", "ana@qsoft.com", "secret123")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectUserWithoutPasswordHash() {
        Tenant tenant = activeTenant("qsoft");
        User user = User.create(UserId.random(), tenant.id(), "sem@qsoft.com", "Sem",
            HierarchyLevel.MANAGER, null);
        usersByTenantAndEmail.put(tenant.id() + "|sem@qsoft.com", user);
        usersById.put(user.id(), user);

        assertThatThrownBy(() -> useCase.login(new LoginCommand("qsoft", "sem@qsoft.com", "x")))
            .isInstanceOf(InvalidCredentialsException.class);
    }
}
