package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RevokeInviteTest {

    private final Map<InviteId, Invite> invitesById = new LinkedHashMap<>();

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

    private final RevokeInvite useCase = new RevokeInvite(inviteRepository);

    private Invite inviteIn(TenantId tenantId, String email) {
        Invite invite = Invite.create(InviteId.random(), tenantId, email,
            HierarchyLevel.COLLABORATOR, null, java.util.UUID.randomUUID(), null,
            "hash-" + email, Instant.now().plusSeconds(3600));
        invitesById.put(invite.id(), invite);
        return invite;
    }

    @Test
    void shouldRevokePendingInvite() {
        TenantId tenantId = TenantId.random();
        Invite invite = inviteIn(tenantId, "ana@x.com");

        useCase.revoke(new RevokeInviteCommand(tenantId, invite.id()));

        assertThat(invitesById.get(invite.id()).status()).isEqualTo(InviteStatus.REVOKED);
    }

    @Test
    void shouldRejectAcceptedInvite() {
        TenantId tenantId = TenantId.random();
        Invite invite = inviteIn(tenantId, "ana@x.com");
        invite.accept(Instant.now());

        assertThatThrownBy(() -> useCase.revoke(new RevokeInviteCommand(tenantId, invite.id())))
            .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void shouldRejectInviteFromAnotherTenant() {
        Invite invite = inviteIn(TenantId.random(), "ana@x.com");

        assertThatThrownBy(() -> useCase.revoke(
            new RevokeInviteCommand(TenantId.random(), invite.id())))
            .isInstanceOf(InvalidInviteException.class);
        assertThat(invitesById.get(invite.id()).status()).isEqualTo(InviteStatus.PENDING);
    }

    @Test
    void shouldRejectUnknownInvite() {
        assertThatThrownBy(() -> useCase.revoke(
            new RevokeInviteCommand(TenantId.random(), InviteId.random())))
            .isInstanceOf(InvalidInviteException.class);
    }
}
