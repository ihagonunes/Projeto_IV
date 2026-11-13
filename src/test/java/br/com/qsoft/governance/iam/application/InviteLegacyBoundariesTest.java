package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteLegacyBoundariesTest {
    private static final String TOKEN = "test-invite";
    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final InviteRepository invites = mock(InviteRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GrantAuthority authority = mock(GrantAuthority.class);

    @Test
    void listHidesInvitesWithoutKnownDelegableSystemRole() {
        Role custom = Role.create(RoleId.random(), tenant, "CUSTOM");
        Role collaborator = Role.create(RoleId.random(), tenant, "COLLABORATOR");
        when(roles.findById(custom.id())).thenReturn(Optional.of(custom));
        when(roles.findById(collaborator.id())).thenReturn(Optional.of(collaborator));
        Invite allowed = invite(collaborator.id().value(), actor.value());
        when(invites.findByTenantId(tenant)).thenReturn(List.of(
            invite(null, actor.value()), invite(UUID.randomUUID(), actor.value()),
            invite(custom.id().value(), actor.value()), allowed));
        when(authority.canGrantByInvite(tenant, actor, SystemRole.COLLABORATOR, null)).thenReturn(true);

        assertThat(new ListInvites(invites, roles, authority).list(tenant, actor)).containsExactly(allowed);
    }

    @Test
    void rejectsResendAndRevocationForLegacyInviteOrLostAuthority() {
        var resend = new ResendInvite(users, roles, invites, authority, 48);
        var revoke = new RevokeInvite(invites, roles, authority);
        Invite missingRole = invite(null, actor.value());
        when(invites.findById(missingRole.id())).thenReturn(Optional.of(missingRole));
        assertThatThrownBy(() -> resend.resend(new ResendInviteCommand(tenant, missingRole.id(), actor)))
            .isInstanceOf(InvalidInviteException.class);
        assertThatThrownBy(() -> revoke.revoke(new RevokeInviteCommand(tenant, missingRole.id(), actor)))
            .isInstanceOf(InvalidInviteException.class);

        Role role = Role.create(RoleId.random(), tenant, "COLLABORATOR");
        Invite unauthorized = invite(role.id().value(), actor.value());
        when(roles.findById(role.id())).thenReturn(Optional.of(role));
        when(invites.findById(unauthorized.id())).thenReturn(Optional.of(unauthorized));
        assertThatThrownBy(() -> resend.resend(new ResendInviteCommand(tenant, unauthorized.id(), actor)))
            .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> revoke.revoke(new RevokeInviteCommand(tenant, unauthorized.id(), actor)))
            .isInstanceOf(AuthorizationDeniedException.class);
        verify(invites, never()).save(any());
    }

    @Test
    void cannotAcceptInvitesWithoutRoleOrInviter() {
        var provision = mock(ProvisionUser.class);
        var grants = mock(RoleAssignmentRepository.class);
        var accept = new AcceptInvite(invites, roles, grants, authority, provision);
        when(invites.findByTokenHash(InviteTokenHasher.sha256Hex(TOKEN))).thenReturn(
            Optional.of(invite(null, actor.value())), Optional.of(invite(UUID.randomUUID(), null)));

        assertThatThrownBy(() -> accept.accept(new AcceptInviteCommand(null, "Pessoa", "test-password")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token");
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> accept.accept(new AcceptInviteCommand(TOKEN, "Pessoa", "test-password")))
                .isInstanceOf(InvalidInviteException.class);
        }
        verifyNoInteractions(roles, grants, authority, provision);
        verify(invites, never()).save(any());
    }

    private Invite invite(UUID role, UUID inviter) {
        Instant now = Instant.now();
        return Invite.restore(InviteId.random(), tenant, "pessoa@example.com", HierarchyLevel.COLLABORATOR,
            null, role, inviter, InviteTokenHasher.sha256Hex(TOKEN), InviteStatus.PENDING,
            now, now.plusSeconds(3600), null);
    }
}
