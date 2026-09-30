package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CreateInviteTest {

    private final Map<TenantId, Tenant> tenants = new LinkedHashMap<>();
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Invite> invitesByToken = new LinkedHashMap<>();

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant tenant) {
            tenants.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenants.get(id));
        }

        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return tenants.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenants.values().stream().anyMatch(t -> t.slug().equals(slug));
        }
    };

    private final UserRepository userRepository = new UserRepository() {
        @Override
        public User save(User user) {
            users.put(user.tenantId() + "|" + user.email(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return users.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return Optional.ofNullable(users.get(tenantId + "|" + email));
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return users.containsKey(tenantId + "|" + email);
        }
    };

    private final InviteRepository inviteRepository = new InviteRepository() {
        @Override
        public Invite save(Invite invite) {
            invitesByToken.put(invite.token(), invite);
            return invite;
        }

        @Override
        public Optional<Invite> findById(InviteId id) {
            return invitesByToken.values().stream().filter(i -> i.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Invite> findByToken(String token) {
            return Optional.ofNullable(invitesByToken.get(token));
        }

        @Override
        public boolean existsByToken(String token) {
            return invitesByToken.containsKey(token);
        }

        @Override
        public List<Invite> findByTenantId(TenantId tenantId) {
            return invitesByToken.values().stream().filter(i -> i.tenantId().equals(tenantId)).toList();
        }
    };

    private final CreateInvite useCase = new CreateInvite(tenantRepository, userRepository, inviteRepository, 48);

    private TenantId activeTenant() {
        TenantId id = TenantId.random();
        tenants.put(id, Tenant.restore(id, "t-" + id, "Tenant", TenantStatus.ACTIVE, Instant.now(), Instant.now()));
        return id;
    }

    @Test
    void shouldCreateInviteWithTokenAndExpiration() {
        TenantId tenantId = activeTenant();

        Invite invite = useCase.create(new CreateInviteCommand(
            tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR, null));

        assertThat(invite.id()).isNotNull();
        assertThat(invite.tenantId()).isEqualTo(tenantId);
        assertThat(invite.email()).isEqualTo("ana@x.com");
        assertThat(invite.status()).isEqualTo(InviteStatus.PENDING);
        assertThat(invite.token()).isNotBlank();
        assertThat(invite.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldRejectWhenUserAlreadyExists() {
        TenantId tenantId = activeTenant();
        users.put(tenantId + "|ana@x.com", User.create(UserId.random(), tenantId,
            "ana@x.com", "Ana", HierarchyLevel.COLLABORATOR, null));

        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(
            tenantId, "ana@x.com", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(
            TenantId.random(), "ana@x.com", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldRejectBlankEmail() {
        TenantId tenantId = activeTenant();

        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(
            tenantId, "  ", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // =====================================================================
    // Branches faltantes (branch 66.7% → ≥ 70%)
    // =====================================================================

    /** Command nulo → IllegalArgumentException imediato. */
    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> useCase.create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    /** Tenant inativo → TenantNotFoundException (mesmo comportamento de inexistente). */
    @Test
    void deve_lancarExcecao_quando_tenantInativo() {
        TenantId id = TenantId.random();
        tenants.put(id, Tenant.restore(id, "off", "Off", TenantStatus.INACTIVE,
            Instant.now(), Instant.now()));

        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(
            id, "ana@x.com", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    /** hierarchyLevel nulo → NullPointerException (requireNonNull do domínio). */
    @Test
    void deve_lancarExcecao_quando_hierarchyLevelNulo() {
        TenantId tenantId = activeTenant();

        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(
            tenantId, "ana@x.com", null, null)))
            .isInstanceOf(NullPointerException.class);
    }

    /** tenantId nulo → NullPointerException (requireNonNull do caso de uso). */
    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(
            null, "ana@x.com", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(NullPointerException.class);
    }
}

