package br.com.qsoft.governance.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RolePersistenceTest {

    @Mock
    private RoleJpaRepository jpaRepository;

    @InjectMocks
    private RoleRepositoryAdapter adapter;

    @Test
    void deve_testarRolePermissionEmbeddable_gettersSettersEqualsEHashCode() {
        RolePermissionEmbeddable defaultConst = new RolePermissionEmbeddable();
        assertThat(defaultConst).isNotNull();

        RolePermissionEmbeddable embeddable1 = new RolePermissionEmbeddable(Resource.MODEL, Action.READ);
        assertThat(embeddable1.getResource()).isEqualTo(Resource.MODEL);
        assertThat(embeddable1.getAction()).isEqualTo(Action.READ);

        embeddable1.setResource(Resource.DEPARTMENT);
        embeddable1.setAction(Action.UPDATE);
        assertThat(embeddable1.getResource()).isEqualTo(Resource.DEPARTMENT);
        assertThat(embeddable1.getAction()).isEqualTo(Action.UPDATE);

        RolePermissionEmbeddable embeddable2 = new RolePermissionEmbeddable(Resource.DEPARTMENT, Action.UPDATE);
        RolePermissionEmbeddable embeddable3 = new RolePermissionEmbeddable(Resource.DEPARTMENT, Action.DELETE);
        RolePermissionEmbeddable embeddable4 = new RolePermissionEmbeddable(Resource.DEPARTMENT, Action.READ);
        RolePermissionEmbeddable embeddable5 = new RolePermissionEmbeddable(Resource.ROLE, Action.UPDATE);

        // equals same instance
        assertThat(embeddable1.equals(embeddable1)).isTrue();
        // equals equal object
        assertThat(embeddable1.equals(embeddable2)).isTrue();
        assertThat(embeddable1.hashCode()).isEqualTo(embeddable2.hashCode());
        // equals different object
        assertThat(embeddable1.equals(embeddable3)).isFalse();
        assertThat(embeddable1.equals(embeddable4)).isFalse();
        assertThat(embeddable1.equals(embeddable5)).isFalse();
        // equals other class / null
        assertThat(embeddable1.equals("string")).isFalse();
        assertThat(embeddable1.equals(null)).isFalse();
    }

    @Test
    void deve_testarRoleJpaEntity_gettersESetters() {
        RoleJpaEntity entity = new RoleJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setName("Manager");
        RolePermissionEmbeddable perm = new RolePermissionEmbeddable(Resource.MODEL, Action.READ);
        entity.setPermissions(Set.of(perm));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getName()).isEqualTo("Manager");
        assertThat(entity.getPermissions()).containsExactly(perm);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);

        // test setPermissions null branch
        entity.setPermissions(null);
        assertThat(entity.getPermissions()).isEmpty();
    }

    @Test
    void deve_converterEntreRoleEEntity_comRoleJpaMapper() {
        RoleJpaMapper mapper = new RoleJpaMapper();
        RoleId roleId = RoleId.random();
        TenantId tenantId = TenantId.random();
        Instant now = Instant.now();

        Role role = Role.restore(
            roleId,
            tenantId,
            "Operator",
            Set.of(Permission.of(Resource.ROLE, Action.READ)),
            now,
            now
        );

        RoleJpaEntity entity = mapper.toEntity(role);
        assertThat(entity.getId()).isEqualTo(roleId.value());
        assertThat(entity.getName()).isEqualTo("Operator");
        assertThat(entity.getPermissions()).hasSize(1);

        Role restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(roleId);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.name()).isEqualTo("Operator");
        assertThat(restored.permissions()).containsExactly(Permission.of(Resource.ROLE, Action.READ));
    }

    @Test
    void deve_salvarRole() {
        Role role = createSampleRole();
        when(jpaRepository.save(any(RoleJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Role saved = adapter.save(role);
        assertThat(saved.id()).isEqualTo(role.id());
        verify(jpaRepository).save(any(RoleJpaEntity.class));
    }

    @Test
    void deve_buscarPorId() {
        Role role = createSampleRole();
        RoleJpaEntity entity = new RoleJpaMapper().toEntity(role);

        when(jpaRepository.findById(role.id().value())).thenReturn(Optional.of(entity));

        Optional<Role> result = adapter.findById(role.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(role.id());
    }

    @Test
    void deve_buscarPorTenantIdEName() {
        TenantId tenantId = TenantId.random();
        Role role = createSampleRoleWith(tenantId, "Admin");
        RoleJpaEntity entity = new RoleJpaMapper().toEntity(role);

        when(jpaRepository.findByTenantIdAndName(tenantId.value(), "Admin")).thenReturn(Optional.of(entity));

        Optional<Role> result = adapter.findByTenantIdAndName(tenantId, "Admin");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Admin");
    }

    @Test
    void deve_verificarExistsByTenantIdEName() {
        TenantId tenantId = TenantId.random();
        when(jpaRepository.existsByTenantIdAndName(tenantId.value(), "Admin")).thenReturn(true);

        assertThat(adapter.existsByTenantIdAndName(tenantId, "Admin")).isTrue();
    }

    private Role createSampleRole() {
        return createSampleRoleWith(TenantId.random(), "RoleName");
    }

    private Role createSampleRoleWith(TenantId tenantId, String name) {
        return Role.restore(
            RoleId.random(),
            tenantId,
            name,
            Set.of(),
            Instant.now(),
            Instant.now()
        );
    }
}
