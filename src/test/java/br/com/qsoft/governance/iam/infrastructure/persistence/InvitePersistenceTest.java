package br.com.qsoft.governance.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitePersistenceTest {

    @Mock
    private InviteJpaRepository jpaRepository;

    @InjectMocks
    private InviteRepositoryAdapter adapter;

    @Test
    void deve_testarInviteJpaEntity_gettersESetters() {
        InviteJpaEntity entity = new InviteJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setEmail("invitee@qsoft.com");
        entity.setHierarchyLevel(HierarchyLevel.COLLABORATOR);
        entity.setDepartmentId(deptId);
        entity.setRoleId(UUID.randomUUID());
        entity.setInvitedBy(UUID.randomUUID());
        entity.setTokenHash("token123");
        entity.setStatus(InviteStatus.PENDING);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plusSeconds(3600));
        entity.setAcceptedAt(now.plusSeconds(600));

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getEmail()).isEqualTo("invitee@qsoft.com");
        assertThat(entity.getHierarchyLevel()).isEqualTo(HierarchyLevel.COLLABORATOR);
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
        assertThat(entity.getTokenHash()).isEqualTo("token123");
        assertThat(entity.getStatus()).isEqualTo(InviteStatus.PENDING);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getExpiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(entity.getAcceptedAt()).isEqualTo(now.plusSeconds(600));
    }

    @Test
    void deve_converterEntreInviteEEntity_comInviteJpaMapper() {
        InviteJpaMapper mapper = new InviteJpaMapper();
        InviteId inviteId = InviteId.random();
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        Instant now = Instant.now();

        UUID roleId = UUID.randomUUID();
        Invite invite = Invite.restore(
            inviteId,
            tenantId,
            "user@qsoft.com",
            HierarchyLevel.MANAGER,
            deptId,
            roleId,
            null,
            "tok-abc",
            InviteStatus.ACCEPTED,
            now,
            now.plusSeconds(3600),
            now.plusSeconds(1800)
        );

        InviteJpaEntity entity = mapper.toEntity(invite);
        assertThat(entity.getId()).isEqualTo(inviteId.value());
        assertThat(entity.getTokenHash()).isEqualTo("tok-abc");

        Invite restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(inviteId);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.email()).isEqualTo("user@qsoft.com");
        assertThat(restored.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(restored.departmentId()).isEqualTo(deptId);
        assertThat(restored.roleId()).isEqualTo(roleId);
        assertThat(restored.tokenHash()).isEqualTo("tok-abc");
        assertThat(restored.status()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(restored.createdAt()).isEqualTo(now);
        assertThat(restored.expiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(restored.acceptedAt()).isEqualTo(now.plusSeconds(1800));
    }

    @Test
    void deve_salvarInvite() {
        Invite invite = createSampleInvite();
        when(jpaRepository.save(any(InviteJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Invite saved = adapter.save(invite);
        assertThat(saved.id()).isEqualTo(invite.id());
        verify(jpaRepository).save(any(InviteJpaEntity.class));
    }

    @Test
    void deve_buscarPorId() {
        Invite invite = createSampleInvite();
        InviteJpaEntity entity = new InviteJpaMapper().toEntity(invite);

        when(jpaRepository.findById(invite.id().value())).thenReturn(Optional.of(entity));

        Optional<Invite> result = adapter.findById(invite.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(invite.id());
    }

    @Test
    void deve_buscarPorTokenHash() {
        Invite invite = createSampleInvite();
        InviteJpaEntity entity = new InviteJpaMapper().toEntity(invite);

        when(jpaRepository.findByTokenHash("token-123")).thenReturn(Optional.of(entity));

        Optional<Invite> result = adapter.findByTokenHash("token-123");
        assertThat(result).isPresent();
        assertThat(result.get().tokenHash()).isEqualTo("token-123");
    }

    @Test
    void deve_verificarExistsByTokenHash() {
        when(jpaRepository.existsByTokenHash("token-123")).thenReturn(true);
        assertThat(adapter.existsByTokenHash("token-123")).isTrue();
    }

    @Test
    void deve_buscarPorTenantId() {
        TenantId tenantId = TenantId.random();
        Invite invite = createSampleInviteWith(tenantId);
        InviteJpaEntity entity = new InviteJpaMapper().toEntity(invite);

        when(jpaRepository.findByTenantId(tenantId.value())).thenReturn(List.of(entity));

        List<Invite> results = adapter.findByTenantId(tenantId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).tenantId()).isEqualTo(tenantId);
    }

    private Invite createSampleInvite() {
        return createSampleInviteWith(TenantId.random());
    }

    private Invite createSampleInviteWith(TenantId tenantId) {
        return Invite.create(
            InviteId.random(),
            tenantId,
            "user@qsoft.com",
            HierarchyLevel.COLLABORATOR,
            null,
            UUID.randomUUID(),
            null,
            "token-123",
            Instant.now().plusSeconds(3600)
        );
    }
}
