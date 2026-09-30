package br.com.qsoft.governance.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
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
    void deve_testarRoleAssignmentJpaEntity_gettersESetters() {
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        entity.setDepartmentId(deptId);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getRoleId()).isEqualTo(roleId);
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
    }

    @Test
    void deve_converterEntreRoleAssignmentEEntity_comRoleAssignmentJpaMapper() {
        RoleAssignmentJpaMapper mapper = new RoleAssignmentJpaMapper();
        RoleAssignmentId id = RoleAssignmentId.random();
        UserId userId = UserId.random();
        RoleId roleId = RoleId.random();
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();

        RoleAssignment assignment = new RoleAssignment(id, userId, roleId, tenantId, deptId);

        RoleAssignmentJpaEntity entity = mapper.toEntity(assignment);
        assertThat(entity.getId()).isEqualTo(id.value());
        assertThat(entity.getUserId()).isEqualTo(userId.value());
        assertThat(entity.getRoleId()).isEqualTo(roleId.value());
        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);

        RoleAssignment restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.userId()).isEqualTo(userId);
        assertThat(restored.roleId()).isEqualTo(roleId);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.departmentId()).isEqualTo(deptId);
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
            RoleAssignmentId.random(), userId, RoleId.random(), tenantId, null);
        RoleAssignmentJpaEntity entity = new RoleAssignmentJpaMapper().toEntity(assignment);

        when(jpaRepository.findByTenantIdAndUserId(tenantId.value(), userId.value()))
            .thenReturn(List.of(entity));

        List<RoleAssignment> results = adapter.findByTenantIdAndUserId(tenantId, userId);
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
            null
        );
    }
}
