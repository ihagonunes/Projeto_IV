package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
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

class ProvisionUserTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();
    private final Map<String, User> usersByTenantAndEmail = new LinkedHashMap<>();
    private final Map<UserId, User> usersById = new LinkedHashMap<>();

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant tenant) {
            tenantsById.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenantsById.get(id));
        }

        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return tenantsById.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenantsById.values().stream().anyMatch(t -> t.slug().equals(slug));
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

    private final ProvisionUser useCase = new ProvisionUser(tenantRepository, userRepository, encoder);

    private Tenant activeTenant() {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(id, "qsoft", "QSOFT", TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(id, tenant);
        return tenant;
    }

    @Test
    void shouldProvisionUserWithPassword() {
        Tenant tenant = activeTenant();

        User user = useCase.provision(new ProvisionUserCommand(
            tenant.id(), "admin@qsoft.com", "Admin", HierarchyLevel.EXECUTIVE,
            "Qsoft@2026", null));

        assertThat(user.id()).isNotNull();
        assertThat(user.tenantId()).isEqualTo(tenant.id());
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.hasPasswordHash()).isTrue();
        assertThat(encoder.matches("Qsoft@2026", user.passwordHash())).isTrue();
    }

    @Test
    void shouldRejectDuplicateEmail() {
        Tenant tenant = activeTenant();
        useCase.provision(new ProvisionUserCommand(
            tenant.id(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, "senha123", null));

        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(
            tenant.id(), "ana@qsoft.com", "Outra", HierarchyLevel.LEADER, "outra123", null)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(
            TenantId.random(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, "senha123", null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldRejectBlankPassword() {
        Tenant tenant = activeTenant();

        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(
            tenant.id(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, "  ", null)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
