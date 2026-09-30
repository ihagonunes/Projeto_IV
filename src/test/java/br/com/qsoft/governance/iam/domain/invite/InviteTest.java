package br.com.qsoft.governance.iam.domain.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InviteTest {

    private final TenantId tenantId = TenantId.random();

    private Invite invite(Instant expiresAt) {
        return Invite.create(InviteId.random(), tenantId, "ana@x.com",
            HierarchyLevel.COLLABORATOR, null, "token-123", expiresAt);
    }

    @Test
    void shouldCreatePendingInvite() {
        Invite invite = invite(Instant.now().plusSeconds(3600));

        assertThat(invite.status()).isEqualTo(InviteStatus.PENDING);
        assertThat(invite.isPending()).isTrue();
        assertThat(invite.email()).isEqualTo("ana@x.com");
        assertThat(invite.token()).isEqualTo("token-123");
        assertThat(invite.acceptedAt()).isNull();
        assertThat(invite.createdAt()).isNotNull();
    }

    @Test
    void shouldAcceptPendingInvite() {
        Invite invite = invite(Instant.now().plusSeconds(3600));
        Instant now = Instant.now();

        invite.accept(now);

        assertThat(invite.status()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(invite.acceptedAt()).isEqualTo(now);
        assertThat(invite.isPending()).isFalse();
    }

    @Test
    void shouldRejectExpiredInvite() {
        Invite invite = invite(Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> invite.accept(Instant.now()))
            .isInstanceOf(InvalidInviteException.class);
        assertThat(invite.isPending()).isTrue();
    }

    @Test
    void shouldRejectReuse() {
        Invite invite = invite(Instant.now().plusSeconds(3600));
        invite.accept(Instant.now());

        assertThatThrownBy(() -> invite.accept(Instant.now()))
            .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThatThrownBy(() -> Invite.create(InviteId.random(), tenantId, "  ",
            HierarchyLevel.COLLABORATOR, null, "t", Instant.now().plusSeconds(60)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRevokePendingInvite() {
        Invite invite = invite(Instant.now().plusSeconds(3600));
        Instant now = Instant.now();

        invite.revoke();

        assertThat(invite.status()).isEqualTo(InviteStatus.REVOKED);
        assertThat(invite.isPending()).isFalse();
    }

    @Test
    void shouldTrimEmailWhenValid() {
        Invite invite = Invite.create(
            InviteId.random(),
            TenantId.random(),
            "  user@domain.com  ",
            HierarchyLevel.COLLABORATOR,
            UUID.randomUUID(),
            "token-123",
            Instant.now().plusSeconds(3600)
        );

        assertThat(invite.email()).isEqualTo("user@domain.com");
    }

    @Test
    void shouldThrowExceptionWhenEmailIsNull() {
        assertThatThrownBy(() -> Invite.create(
            InviteId.random(),
            TenantId.random(),
            null,
            HierarchyLevel.COLLABORATOR,
            UUID.randomUUID(),
            "token-123",
            Instant.now().plusSeconds(3600)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email é obrigatório.");
    }

    @Test
    void shouldThrowExceptionWhenEmailIsEmpty() {
        assertThatThrownBy(() -> Invite.create(
            InviteId.random(),
            TenantId.random(),
            "",
            HierarchyLevel.COLLABORATOR,
            UUID.randomUUID(),
            "token-123",
            Instant.now().plusSeconds(3600)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email é obrigatório.");
    }

    @Test
    void shouldThrowExceptionWhenEmailIsBlank() {
        assertThatThrownBy(() -> Invite.create(
            InviteId.random(),
            TenantId.random(),
            "   ",
            HierarchyLevel.COLLABORATOR,
            UUID.randomUUID(),
            "token-123",
            Instant.now().plusSeconds(3600)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email é obrigatório.");
    }

    @Test
    void shouldRevokeInviteWhenStatusIsPending() {
        Invite invite = Invite.create(
            InviteId.random(),
            TenantId.random(),
            "user@domain.com",
            HierarchyLevel.COLLABORATOR,
            UUID.randomUUID(),
            "token-123",
            Instant.now().plusSeconds(3600)
        );

        Invite revokedInvite = invite.revoke();

        assertThat(revokedInvite.status()).isEqualTo(InviteStatus.REVOKED);
        assertThat(revokedInvite.isPending()).isFalse();
    }

    @Test
    void shouldNotChangeStatusWhenRevokingNonPendingInvite() {
        Instant now = Instant.now();
        Invite invite = Invite.create(
            InviteId.random(),
            TenantId.random(),
            "user@domain.com",
            HierarchyLevel.COLLABORATOR,
            UUID.randomUUID(),
            "token-123",
            now.plusSeconds(3600)
        );

        // Aceita o convite primeiro para alterar o status de PENDING para ACCEPTED
        invite.accept(now);

        // Tenta revogar um convite que já não está mais PENDING
        Invite result = invite.revoke();

        // Garante que o status permanece ACCEPTED e a branch 'if' não executou
        assertThat(result.status()).isEqualTo(InviteStatus.ACCEPTED);
    }
}
