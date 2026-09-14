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

class CreateUserTest {

    private final Map<String, User> usersByTenantAndEmail = new LinkedHashMap<>();
    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();

    private final Map<UserId, User> usersById = new LinkedHashMap<>();

    private final UserRepository userRepository = new UserRepository() {
        @Override
        public User save(User user) {
            usersByTenantAndEmail.put(key(user.tenantId(), user.email()), user);
            usersById.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return Optional.ofNullable(usersByTenantAndEmail.get(key(tenantId, email)));
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersByTenantAndEmail.containsKey(key(tenantId, email));
        }
    };

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
            return tenantsById.values().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenantsById.values().stream().anyMatch(t -> t.slug().equals(slug));
        }
    };

    private final CreateUser useCase = new CreateUser(userRepository, tenantRepository);

    private String key(TenantId tenantId, String email) {
        return tenantId + "|" + email;
    }

    private Tenant activeTenant() {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(id, "qsoft", "QSOFT", TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(id, tenant);
        return tenant;
    }

    @Test
    void shouldCreateUserInTenantAsActive() {
        Tenant tenant = activeTenant();

        User user = useCase.create(new CreateUserCommand(
            tenant.id(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null));

        assertThat(user.id()).isNotNull();
        assertThat(user.tenantId()).isEqualTo(tenant.id());
        assertThat(user.email()).isEqualTo("ana@qsoft.com");
        assertThat(user.name()).isEqualTo("Ana");
        assertThat(user.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateEmailInSameTenant() {
        Tenant tenant = activeTenant();
        useCase.create(new CreateUserCommand(
            tenant.id(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null));

        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(
            tenant.id(), "ana@qsoft.com", "Outra", HierarchyLevel.LEADER, null)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void shouldAllowSameEmailInDifferentTenants() {
        Tenant tenantA = activeTenant();
        Tenant tenantB = activeTenant();

        useCase.create(new CreateUserCommand(
            tenantA.id(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null));
        User inB = useCase.create(new CreateUserCommand(
            tenantB.id(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null));

        assertThat(inB.tenantId()).isEqualTo(tenantB.id());
        assertThat(userRepository.existsByTenantIdAndEmail(tenantA.id(), "ana@qsoft.com")).isTrue();
        assertThat(userRepository.existsByTenantIdAndEmail(tenantB.id(), "ana@qsoft.com")).isTrue();
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(
            TenantId.random(), "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldRejectInactiveTenant() {
        TenantId id = TenantId.random();
        tenantsById.put(id, Tenant.restore(id, "inactive", "Inactive", TenantStatus.INACTIVE,
            Instant.now(), Instant.now()));

        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(
            id, "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldRejectBlankEmail() {
        Tenant tenant = activeTenant();

        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(
            tenant.id(), "  ", "Ana", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * CENÁRIO BDD — planejado por: <b>Ihago</b> (no caso de uso interno)
     *
     * Confirma que o <em>CreateUser</em> também cria o usuário com o papel
     * COLABORATOR. O mesmo cenário, com a camada de autorização do
     * administrador, é coberto por RegisterUserTest.shouldRegisterUserWithCollaboratorRole.
     * Este é um teste de "caracterização": trava o comportamento existente
     * para impedir regressões futuras.
     */
    @Test
    void shouldCreateUserAsCollaborator() {
        Tenant tenant = activeTenant();

        User user = useCase.create(new CreateUserCommand(
            tenant.id(), "bia@qsoft.com", "Bia", HierarchyLevel.COLLABORATOR, null));

        assertThat(user.hierarchyLevel()).isEqualTo(HierarchyLevel.COLLABORATOR);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    /**
     * CENÁRIO BDD — planejado por: <b>Ihan</b> (no caso de uso interno)
     *
     * Papel (hierarchyLevel) é obrigatório também no <em>CreateUser</em>.
     * Obs.: o CreateUser usa Objects.requireNonNull (lança NullPointerException);
     * a mensagem amigável "obrigatório" (IllegalArgumentException) é exposta
     * pelo RegisterUser — coberto por RegisterUserTest.shouldRejectRegistrationWithoutRole.
     */
    @Test
    void shouldRejectNullRole() {
        Tenant tenant = activeTenant();

        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(
            tenant.id(), "ana@qsoft.com", "Ana", null, null)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hierarchyLevelShouldNotContainAdmin() {
        assertThat(java.util.Arrays.stream(HierarchyLevel.values()).map(Enum::name))
            .doesNotContain("ADMIN");
    }
}
