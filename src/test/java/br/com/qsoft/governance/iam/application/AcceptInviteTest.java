package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.InviteAuthorityLostException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Testes unitários de {@link AcceptInvite}. ProvisionUser é instanciado com
 * fakes em memória — mesmo padrão de ProvisionUserTest.
 */
class AcceptInviteTest {

    private static final String VALID_TOKEN = "token-abc-123";
    private static final String VALID_PASS = "Senha@2026";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final TenantId tenantId = TenantId.random();
    private final RoleId roleId = RoleId.random();

    // ── Fakes em memória ──────────────────────────────────────────────────────

    private final Map<String, Invite> invitesByToken = new LinkedHashMap<>();
    private final List<Invite> savedInvites = new ArrayList<>();
    private final Map<RoleId, Role> roles = new LinkedHashMap<>();
    private final List<RoleAssignment> assignments = new ArrayList<>();

    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();
    private final Map<String, User> usersByTenantAndEmail = new LinkedHashMap<>();
    private final Map<UserId, User> usersById = new LinkedHashMap<>();

    private final InviteRepository inviteRepository = new InviteRepository() {
        @Override
        public Invite save(Invite invite) {
            invitesByToken.put(invite.tokenHash(), invite);
            savedInvites.add(invite);
            return invite;
        }
        @Override
        public Optional<Invite> findById(InviteId id) {
            return invitesByToken.values().stream().filter(i -> i.id().equals(id)).findFirst();
        }
        @Override
        public Optional<Invite> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(invitesByToken.get(tokenHash));
        }
        @Override
        public boolean existsByTokenHash(String tokenHash) {
            return invitesByToken.containsKey(tokenHash);
        }
        @Override
        public List<Invite> findByTenantId(TenantId tid) {
            return invitesByToken.values().stream()
                .filter(i -> i.tenantId().equals(tid)).toList();
        }
    };

    private final RoleRepository roleRepository = new RoleRepository() {
        @Override
        public Role save(Role role) {
            roles.put(role.id(), role);
            return role;
        }
        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(roles.get(id));
        }
        @Override
        public Optional<Role> findByTenantIdAndName(TenantId tid, String name) {
            return roles.values().stream()
                .filter(r -> r.tenantId().equals(tid) && r.name().equals(name)).findFirst();
        }
        @Override
        public boolean existsByTenantIdAndName(TenantId tid, String name) {
            return findByTenantIdAndName(tid, name).isPresent();
        }
    };

    private final RoleAssignmentRepository roleAssignmentRepository = new RoleAssignmentRepository() {
        @Override
        public RoleAssignment save(RoleAssignment assignment) {
            assignments.add(assignment);
            return assignment;
        }
        @Override
        public Optional<RoleAssignment> findById(RoleAssignmentId id) {
            return assignments.stream().filter(a -> a.id().equals(id)).findFirst();
        }
        @Override
        public List<RoleAssignment> findByTenantIdAndUserId(TenantId tid, UserId uid) {
            return assignments.stream()
                .filter(a -> a.tenantId().equals(tid) && a.userId().equals(uid)).toList();
        }
        @Override
        public List<RoleAssignment> findActiveByTenantIdAndUserId(TenantId tid, UserId uid) {
            return findByTenantIdAndUserId(tid, uid);
        }
        @Override
        public void deleteById(RoleAssignmentId id) {
            assignments.removeIf(a -> a.id().equals(id));
        }
    };

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant t) { tenantsById.put(t.id(), t); return t; }
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
        public Optional<User> findByTenantIdAndEmail(TenantId tid, String email) {
            return Optional.ofNullable(usersByTenantAndEmail.get(tid + "|" + email));
        }
        @Override
        public boolean existsByTenantIdAndEmail(TenantId tid, String email) {
            return usersByTenantAndEmail.containsKey(tid + "|" + email);
        }
    };

    private final ProvisionUser provisionUser =
        new ProvisionUser(tenantRepository, userRepository, encoder);

    private final GrantAuthority grantAuthority = mock(GrantAuthority.class);

    private final AcceptInvite useCase = new AcceptInvite(
        inviteRepository, roleRepository, roleAssignmentRepository, grantAuthority, provisionUser);

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String hash(String rawToken) {
        return InviteTokenHasher.sha256Hex(rawToken);
    }

    private void activeTenant() {
        Tenant t = Tenant.restore(tenantId, "qsoft", "QSOFT",
            TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(tenantId, t);
        roles.put(roleId, Role.create(roleId, tenantId, "COLLABORATOR"));
    }

    private Invite pendingInvite(String token, Instant expiresAt) {
        Invite invite = Invite.create(InviteId.random(), tenantId, "convidado@qsoft.com",
            HierarchyLevel.COLLABORATOR, null, roleId.value(), UUID.randomUUID(),
            hash(token), expiresAt);
        invitesByToken.put(hash(token), invite);
        return invite;
    }

    private Invite expiredInvite(String token) {
        return Invite.restore(InviteId.random(), tenantId, "exp@qsoft.com",
            HierarchyLevel.COLLABORATOR, null, roleId.value(), UUID.randomUUID(), hash(token),
            InviteStatus.PENDING, Instant.now().minusSeconds(7200),
            Instant.now().minusSeconds(3600), null);
    }

    private Invite alreadyAcceptedInvite(String token) {
        return Invite.restore(InviteId.random(), tenantId, "used@qsoft.com",
            HierarchyLevel.COLLABORATOR, null, roleId.value(), UUID.randomUUID(), hash(token),
            InviteStatus.ACCEPTED, Instant.now().minusSeconds(7200),
            Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1800));
    }

    @BeforeEach
    void allowDelegation() {
        when(grantAuthority.canGrantByInvite(any(), any(), any(), any())).thenReturn(true);
    }

    // ── Testes ────────────────────────────────────────────────────────────────

    @Test
    void deve_aceitarConvite_quando_dadosValidos_semNome() {
        activeTenant();
        pendingInvite(VALID_TOKEN, Instant.now().plusSeconds(3600));

        User user = useCase.accept(new AcceptInviteCommand(VALID_TOKEN, null, VALID_PASS));

        assertThat(user.name()).isEqualTo("convidado@qsoft.com");
        assertThat(user.email()).isEqualTo("convidado@qsoft.com");
        assertThat(user.tenantId()).isEqualTo(tenantId);
        assertThat(savedInvites).hasSize(1);
        // Cria a concessão INVITE na mesma transação.
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).userId()).isEqualTo(user.id());
    }

    @Test
    void deve_aceitarConvite_quando_dadosValidos_comNome() {
        activeTenant();
        pendingInvite(VALID_TOKEN, Instant.now().plusSeconds(3600));

        User user = useCase.accept(new AcceptInviteCommand(VALID_TOKEN, "  Maria  ", VALID_PASS));

        assertThat(user.name()).isEqualTo("Maria");
        assertThat(savedInvites).hasSize(1);
    }

    @Test
    void deve_aceitarConvite_quando_nomeEmBranco() {
        activeTenant();
        pendingInvite(VALID_TOKEN, Instant.now().plusSeconds(3600));

        User user = useCase.accept(new AcceptInviteCommand(VALID_TOKEN, "   ", VALID_PASS));

        assertThat(user.name()).isEqualTo("convidado@qsoft.com");
        assertThat(savedInvites).hasSize(1);
    }

    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> useCase.accept(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(savedInvites).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_tokenVazio() {
        assertThatThrownBy(() -> useCase.accept(new AcceptInviteCommand("", null, VALID_PASS)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("token");

        assertThat(savedInvites).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_passwordVazia() {
        assertThatThrownBy(() -> useCase.accept(new AcceptInviteCommand(VALID_TOKEN, null, "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("password");

        assertThat(savedInvites).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_tokenNaoEncontrado() {
        assertThatThrownBy(() ->
            useCase.accept(new AcceptInviteCommand("token-inexistente", null, VALID_PASS)))
            .isInstanceOf(InvalidInviteException.class);

        assertThat(savedInvites).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_conviteExpirado() {
        invitesByToken.put(hash(VALID_TOKEN), expiredInvite(VALID_TOKEN));

        assertThatThrownBy(() ->
            useCase.accept(new AcceptInviteCommand(VALID_TOKEN, null, VALID_PASS)))
            .isInstanceOf(InvalidInviteException.class);

        assertThat(savedInvites).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_conviteJaAceito() {
        invitesByToken.put(hash(VALID_TOKEN), alreadyAcceptedInvite(VALID_TOKEN));

        assertThatThrownBy(() ->
            useCase.accept(new AcceptInviteCommand(VALID_TOKEN, null, VALID_PASS)))
            .isInstanceOf(InvalidInviteException.class);

        assertThat(savedInvites).isEmpty();
    }

    @Test
    void deve_retornar409_quando_convidantePerdeuAutoridade() {
        activeTenant();
        pendingInvite(VALID_TOKEN, Instant.now().plusSeconds(3600));
        when(grantAuthority.canGrantByInvite(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
            useCase.accept(new AcceptInviteCommand(VALID_TOKEN, null, VALID_PASS)))
            .isInstanceOf(InviteAuthorityLostException.class);

        assertThat(assignments).isEmpty();
        assertThat(userRepository.existsByTenantIdAndEmail(tenantId, "convidado@qsoft.com")).isFalse();
    }
}
