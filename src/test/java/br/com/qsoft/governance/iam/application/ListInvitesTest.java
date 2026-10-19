package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.invite.Invite;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListInvitesTest {

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private GrantAuthority grantAuthority;

    @InjectMocks
    private ListInvites listInvites;

    private final TenantId tenantId = TenantId.random();
    private final UserId actor = UserId.random();

    @Test
    void deve_listar_apenas_convites_que_o_ator_pode_conceder() {
        RoleId roleId = RoleId.random();
        Role role = Role.create(roleId, tenantId, "COLLABORATOR");
        Invite invite = Invite.create(InviteId.random(), tenantId, "user@qsoft.com",
            HierarchyLevel.COLLABORATOR, null, roleId.value(), actor.value(),
            "hash-1", Instant.now().plusSeconds(86400));

        when(inviteRepository.findByTenantId(tenantId)).thenReturn(List.of(invite));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(grantAuthority.canGrantByInvite(tenantId, actor, SystemRole.COLLABORATOR, null))
            .thenReturn(true);

        List<Invite> result = listInvites.list(tenantId, actor);

        assertThat(result).containsExactly(invite);
    }

    @Test
    void deve_ocultar_convites_que_o_ator_nao_pode_conceder() {
        RoleId roleId = RoleId.random();
        Role role = Role.create(roleId, tenantId, "TENANT_ADMIN");
        Invite invite = Invite.create(InviteId.random(), tenantId, "user@qsoft.com",
            HierarchyLevel.EXECUTIVE, null, roleId.value(), actor.value(),
            "hash-1", Instant.now().plusSeconds(86400));

        when(inviteRepository.findByTenantId(tenantId)).thenReturn(List.of(invite));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(grantAuthority.canGrantByInvite(tenantId, actor, SystemRole.TENANT_ADMIN, null))
            .thenReturn(false);

        assertThat(listInvites.list(tenantId, actor)).isEmpty();
    }

    @Test
    void deve_validar_tenant_id_obrigatorio() {
        assertThatThrownBy(() -> listInvites.list(null, actor))
            .isInstanceOf(NullPointerException.class);
    }
}
