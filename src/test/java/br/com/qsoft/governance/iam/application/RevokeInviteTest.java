package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeInviteTest {

    private final Map<InviteId, Invite> invitesById = new LinkedHashMap<>();
    private final Map<String, Role> roles = new LinkedHashMap<>();

    private final RoleId roleId = RoleId.random();
    private final UserId actor = UserId.random();

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

    private final GrantAuthority grantAuthority = mock(GrantAuthority.class);

    private final RevokeInvite useCase =
        new RevokeInvite(inviteRepository, roleRepository, grantAuthority);

    @BeforeEach
    void setUp() {
        when(grantAuthority.canGrantByInvite(any(), any(), any(), any())).thenReturn(true);
    }

    private Invite inviteIn(TenantId tenantId, String email) {
        roles.putIfAbsent(tenantId + "|COLLABORATOR",
            Role.create(roleId, tenantId, "COLLABORATOR"));
        Invite invite = Invite.create(InviteId.random(), tenantId, email,
            HierarchyLevel.COLLABORATOR, null, roleId.value(), actor.value(),
            "hash-" + email, Instant.now().plusSeconds(3600));
        invitesById.put(invite.id(), invite);
        return invite;
    }

    @Test
    void shouldRevokePendingInvite() {
        TenantId tenantId = TenantId.random();
        Invite invite = inviteIn(tenantId, "ana@x.com");

        useCase.revoke(new RevokeInviteCommand(tenantId, invite.id(), actor));

        assertThat(invitesById.get(invite.id()).status()).isEqualTo(InviteStatus.REVOKED);
    }

    @Test
    void shouldRejectAcceptedInvite() {
        TenantId tenantId = TenantId.random();
        Invite invite = inviteIn(tenantId, "ana@x.com");
        invite.accept(Instant.now());

        assertThatThrownBy(() -> useCase.revoke(new RevokeInviteCommand(tenantId, invite.id(), actor)))
            .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void shouldRejectInviteFromAnotherTenant() {
        Invite invite = inviteIn(TenantId.random(), "ana@x.com");

        assertThatThrownBy(() -> useCase.revoke(
            new RevokeInviteCommand(TenantId.random(), invite.id(), actor)))
            .isInstanceOf(InvalidInviteException.class);
        assertThat(invitesById.get(invite.id()).status()).isEqualTo(InviteStatus.PENDING);
    }

    @Test
    void shouldRejectUnknownInvite() {
        assertThatThrownBy(() -> useCase.revoke(
            new RevokeInviteCommand(TenantId.random(), InviteId.random(), actor)))
            .isInstanceOf(InvalidInviteException.class);
    }
}
