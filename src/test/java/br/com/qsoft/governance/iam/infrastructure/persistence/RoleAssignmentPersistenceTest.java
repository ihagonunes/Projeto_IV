package br.com.qsoft.governance.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentPersistenceTest {

    @Mock
    private RoleAssignmentJpaRepository jpaRepository;

    @InjectMocks
    private RoleAssignmentRepositoryAdapter adapter;

    @Test
    void countsAndLooksUpAdminGrantsWithinTenant() {
        TenantId tenant = TenantId.random();
        RoleId role = RoleId.random();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(jpaRepository.countByTenantIdAndRoleIdAndRevokedAtIsNull(tenant.value(), role.value()))
            .thenReturn(2L);
        when(jpaRepository.findUserIdsWithOnlyTenantAdminGrant(tenant.value()))
            .thenReturn(List.of(first, second, first));

        assertThat(adapter.countActiveByTenantIdAndRoleId(tenant, role)).isEqualTo(2);
        assertThat(adapter.findUserIdsWithOnlyTenantAdminGrant(tenant)).isEqualTo(Set.of(UserId.of(first), UserId.of(second)));
    }

    @Test
    void deve_testarRoleAssignmentJpaEntity_gettersESetters() {
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        entity.setDepartmentId(deptId);
        entity.setScopeType(ScopeType.DEPARTMENT);
        entity.setOrigin(GrantOrigin.INVITE);
        entity.setGrantedBy(grantedBy);
        entity.setGrantedAt(now);
        entity.setRevokedAt(now);
        entity.setRevokedBy(revokedBy);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getRoleId()).isEqualTo(roleId);
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
        assertThat(entity.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(entity.getOrigin()).isEqualTo(GrantOrigin.INVITE);
        assertThat(entity.getGrantedBy()).isEqualTo(grantedBy);
        assertThat(entity.getGrantedAt()).isEqualTo(now);
        assertThat(entity.getRevokedAt()).isEqualTo(now);
        assertThat(entity.getRevokedBy()).isEqualTo(revokedBy);
    }

    @Test
    void deve_converterEntreRoleAssignmentEEntity_comRoleAssignmentJpaMapper() {
        RoleAssignmentJpaMapper mapper = new RoleAssignmentJpaMapper();
        UUID deptId = UUID.randomUUID();
        RoleAssignment assignment = new RoleAssignment(
            RoleAssignmentId.random(), UserId.random(), RoleId.random(), TenantId.random(),
            ScopeType.DEPARTMENT, deptId, GrantOrigin.INVITE, UserId.random(), Instant.now(),
            null, null);

        RoleAssignmentJpaEntity entity = mapper.toEntity(assignment);
        assertThat(entity.getId()).isEqualTo(assignment.id().value());
        assertThat(entity.getUserId()).isEqualTo(assignment.userId().value());
        assertThat(entity.getRoleId()).isEqualTo(assignment.roleId().value());
        assertThat(entity.getTenantId()).isEqualTo(assignment.tenantId().value());
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
        assertThat(entity.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(entity.getOrigin()).isEqualTo(GrantOrigin.INVITE);

        RoleAssignment restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(assignment.id());
        assertThat(restored.userId()).isEqualTo(assignment.userId());
        assertThat(restored.roleId()).isEqualTo(assignment.roleId());
        assertThat(restored.tenantId()).isEqualTo(assignment.tenantId());
        assertThat(restored.departmentId()).isEqualTo(deptId);
        assertThat(restored.scopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(restored.origin()).isEqualTo(GrantOrigin.INVITE);
        assertThat(restored.isActive()).isTrue();
    }

    @Test
    void deve_salvarRoleAssignment() {
        RoleAssignment assignment = createSampleAssignment();
        when(jpaRepository.save(any(RoleAssignmentJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        RoleAssignment saved = adapter.save(assignment);
        assertThat(saved.id()).isEqualTo(assignment.id());
        verify(jpaRepository).save(any(RoleAssignmentJpaEntity.class));
    }

    @Test
    void deve_buscarPorId() {
        RoleAssignment assignment = createSampleAssignment();
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaMapper().toEntity(assignment);

        when(jpaRepository.findById(assignment.id().value())).thenReturn(Optional.of(entity));

        Optional<RoleAssignment> result = adapter.findById(assignment.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(assignment.id());
    }

    @Test
    void deve_buscarPorTenantIdEUserId() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        RoleAssignment assignment = new RoleAssignment(
            RoleAssignmentId.random(), userId, RoleId.random(), tenantId,
            ScopeType.SELF, null, GrantOrigin.MIGRATION, null, Instant.now(), null, null);
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaMapper().toEntity(assignment);

        when(jpaRepository.findByTenantIdAndUserId(tenantId.value(), userId.value()))
            .thenReturn(List.of(entity));

        List<RoleAssignment> results = adapter.findByTenantIdAndUserId(tenantId, userId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(assignment.id());
    }

    @Test
    void deve_buscarAtivasPorTenantIdEUserId() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        RoleAssignment assignment = createSampleAssignment();
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaMapper().toEntity(assignment);

        when(jpaRepository.findByTenantIdAndUserIdAndRevokedAtIsNull(tenantId.value(), userId.value()))
            .thenReturn(List.of(entity));

        List<RoleAssignment> results = adapter.findActiveByTenantIdAndUserId(tenantId, userId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(assignment.id());
    }

    @Test
    void deve_deletarPorId() {
        RoleAssignmentId id = RoleAssignmentId.random();
        adapter.deleteById(id);
        verify(jpaRepository).deleteById(id.value());
    }

    private RoleAssignment createSampleAssignment() {
        return new RoleAssignment(
            RoleAssignmentId.random(),
            UserId.random(),
            RoleId.random(),
            TenantId.random(),
            ScopeType.SELF,
            null,
            GrantOrigin.MIGRATION,
            null,
            Instant.now(),
            null,
            null
        );
    }
}
