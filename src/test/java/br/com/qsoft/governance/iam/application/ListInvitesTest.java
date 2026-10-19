package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListInvitesTest {

    @Mock
    private InviteRepository inviteRepository;

    @InjectMocks
    private ListInvites listInvites;

    private final TenantId tenantId = TenantId.random();

    @Test
    void deve_listar_convites_do_tenant() {
        Invite invite = Invite.create(
            InviteId.random(), tenantId, "user@qsoft.com", HierarchyLevel.COLLABORATOR, null,
            java.util.UUID.randomUUID(), null, "token123", Instant.now().plusSeconds(86400));

        when(inviteRepository.findByTenantId(tenantId)).thenReturn(List.of(invite));

        List<Invite> result = listInvites.list(tenantId);

        assertThat(result).containsExactly(invite);
        verify(inviteRepository).findByTenantId(tenantId);
    }

    @Test
    void deve_validar_tenant_id_obrigatorio() {
        assertThatThrownBy(() -> listInvites.list(null))
            .isInstanceOf(NullPointerException.class);
    }
}
