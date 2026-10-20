package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.InviteNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResendInviteTest {

    private final Map<InviteId, Invite> invitesById = new LinkedHashMap<>();
    private final Map<UserId, User> usersById = new LinkedHashMap<>();
    private final Map<String, Role> roles = new LinkedHashMap<>();

    private final RoleId roleId = RoleId.random();
    private final UserId actor = UserId.random();

    private final UserRepository userRepository = new UserRepository() {
        @Override
        public User save(User user) {
            usersById.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .anyMatch(u -> u.tenantId().equals(tenantId) && u.email().equals(email));
        }
    };

    private final RoleRepository roleRepository = new RoleRepository() {
        @Override
        public Role save(Role role) {
            roles.put(role.tenantId() + "|" + role.name(), role);
            return role;
        }

        @Override
        public Optional<Role> findById(RoleId id) {
            return roles.values().stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Role> findByTenantIdAndName(TenantId tenantId, String name) {
            return Optional.ofNullable(roles.get(tenantId + "|" + name));
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
            return roles.containsKey(tenantId + "|" + name);
        }
    };

    private final InviteRepository inviteRepository = new InviteRepository() {
        @Override
        public Invite save(Invite invite) {
            invitesById.put(invite.id(), invite);
            return invite;
        }

        @Override
        public Optional<Invite> findById(InviteId id) {
            return Optional.ofNullable(invitesById.get(id));
        }

        @Override
        public Optional<Invite> findByTokenHash(String tokenHash) {
            return invitesById.values().stream()
                .filter(i -> i.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public boolean existsByTokenHash(String tokenHash) {
            return invitesById.values().stream().anyMatch(i -> i.tokenHash().equals(tokenHash));
        }

        @Override
        public List<Invite> findByTenantId(TenantId tenantId) {
            return invitesById.values().stream()
                .filter(i -> i.tenantId().equals(tenantId))
                .toList();
        }
    };

    private final GrantAuthority grantAuthority = mock(GrantAuthority.class);

    private final ResendInvite useCase =
        new ResendInvite(userRepository, roleRepository, inviteRepository, grantAuthority, 48);

    @BeforeEach
    void setUp() {
        when(grantAuthority.canGrantByInvite(any(), any(), any(), any())).thenReturn(true);
    }

    private Invite inviteIn(TenantId tenantId, String email, Instant expiresAt) {
        roles.putIfAbsent(tenantId + "|COLLABORATOR",
            Role.create(roleId, tenantId, "COLLABORATOR"));
        Invite invite = Invite.create(InviteId.random(), tenantId, email,
            HierarchyLevel.COLLABORATOR, null, roleId.value(), actor.value(),
            "hash-" + email, expiresAt);
        invitesById.put(invite.id(), invite);
        return invite;
    }

    @Test
    void shouldIssueNewInviteAndRevokePreviousWhenExpired() {
        TenantId tenantId = TenantId.random();
        Invite expired = inviteIn(tenantId, "ana@x.com", Instant.now().minusSeconds(3600));

        IssuedInvite renewed = useCase.resend(
            new ResendInviteCommand(tenantId, expired.id(), actor));

        assertThat(renewed.invite().id()).isNotEqualTo(expired.id());
        assertThat(renewed.invite().email()).isEqualTo("ana@x.com");
        assertThat(renewed.invite().status()).isEqualTo(InviteStatus.PENDING);
        assertThat(renewed.invite().tokenHash()).isNotEqualTo(expired.tokenHash());
        assertThat(renewed.token()).isNotBlank();
        assertThat(renewed.invite().expiresAt()).isAfter(Instant.now());
        assertThat(invitesById.get(expired.id()).status()).isEqualTo(InviteStatus.REVOKED);
    }

    @Test
    void shouldRejectAcceptedInvite() {
        TenantId tenantId = TenantId.random();
        Invite invite = inviteIn(tenantId, "ana@x.com", Instant.now().plusSeconds(3600));
        invite.accept(Instant.now());

        assertThatThrownBy(() -> useCase.resend(new ResendInviteCommand(tenantId, invite.id(), actor)))
            .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void shouldRejectInviteFromAnotherTenant() {
        Invite invite = inviteIn(TenantId.random(), "ana@x.com", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> useCase.resend(
            new ResendInviteCommand(TenantId.random(), invite.id(), actor)))
            .isInstanceOf(InviteNotFoundException.class);
    }

    @Test
    void shouldRejectWhenUserAlreadyExists() {
        TenantId tenantId = TenantId.random();
        Invite invite = inviteIn(tenantId, "ana@x.com", Instant.now().minusSeconds(60));
        usersById.put(UserId.random(), User.create(UserId.random(), tenantId, "ana@x.com",
            "Ana", HierarchyLevel.COLLABORATOR, null));

        assertThatThrownBy(() -> useCase.resend(new ResendInviteCommand(tenantId, invite.id(), actor)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void shouldRejectUnknownInvite() {
        assertThatThrownBy(() -> useCase.resend(
            new ResendInviteCommand(TenantId.random(), InviteId.random(), actor)))
            .isInstanceOf(InviteNotFoundException.class);
    }
}
