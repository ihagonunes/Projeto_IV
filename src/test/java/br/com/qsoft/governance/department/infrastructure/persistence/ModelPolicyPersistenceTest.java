package br.com.qsoft.governance.department.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModelPolicyPersistenceTest {

    @Mock
    private ModelPolicyJpaRepository jpaRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ModelPolicyRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_testarModelPolicyJpaEntity_gettersESetters() {
        ModelPolicyJpaEntity entity = new ModelPolicyJpaEntity();
        UUID deptId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID defaultModelId = UUID.randomUUID();
        UUID allowedModelId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setDepartmentId(deptId);
        entity.setTenantId(tenantId);
        entity.setDefaultModelId(defaultModelId);
        entity.setAllowedModelIds(Set.of(allowedModelId));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getDefaultModelId()).isEqualTo(defaultModelId);
        assertThat(entity.getAllowedModelIds()).containsExactly(allowedModelId);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deve_tratarSetAllowedModelIdsNuloEmEntity() {
        ModelPolicyJpaEntity entity = new ModelPolicyJpaEntity();
        entity.setAllowedModelIds(null);

        assertThat(entity.getAllowedModelIds()).isEmpty();
    }

    @Test
    void deve_converterEntreModelPolicyEEntity_comModelPolicyJpaMapper() {
        ModelPolicyJpaMapper mapper = new ModelPolicyJpaMapper();
        UUID deptId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID defaultModelId = UUID.randomUUID();
        UUID allowedModelId = UUID.randomUUID();
        Instant now = Instant.now();

        ModelPolicy policy = ModelPolicy.restore(
            deptId,
            Set.of(allowedModelId),
            defaultModelId,
            now,
            now
        );

        ModelPolicyJpaEntity entity = mapper.toEntity(policy, tenantId);
        assertThat(entity.getDepartmentId()).isEqualTo(deptId);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getDefaultModelId()).isEqualTo(defaultModelId);
        assertThat(entity.getAllowedModelIds()).containsExactly(allowedModelId);

        ModelPolicy restored = mapper.toDomain(entity);
        assertThat(restored.departmentId()).isEqualTo(deptId);
        assertThat(restored.defaultModelId()).isEqualTo(defaultModelId);
        assertThat(restored.allowedModelIds()).containsExactly(allowedModelId);
    }

    @Test
    void deve_salvarModelPolicy_comTenantDoContexto() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        ModelPolicy policy = samplePolicy();
        when(jpaRepository.save(any(ModelPolicyJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        ModelPolicy saved = adapter.save(policy);
        assertThat(saved.departmentId()).isEqualTo(policy.departmentId());
        verify(jpaRepository).save(any(ModelPolicyJpaEntity.class));
    }

    @Test
    void deve_buscarPorTenantIdEDepartmentId_semTenantContext() {
        TenantId tenantId = TenantId.random();
        ModelPolicy policy = samplePolicy();
        ModelPolicyJpaEntity entity = new ModelPolicyJpaMapper().toEntity(policy, tenantId.value());

        when(jpaRepository.findByTenantIdAndDepartmentId(tenantId.value(), policy.departmentId()))
            .thenReturn(Optional.of(entity));

        Optional<ModelPolicy> result = adapter.findByTenantIdAndDepartmentId(tenantId, policy.departmentId());
        assertThat(result).isPresent();
        assertThat(result.get().departmentId()).isEqualTo(policy.departmentId());
    }

    @Test
    void deve_buscarPorTenantIdEDepartmentId_comTenantContext_filtroNaoHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        ModelPolicy policy = samplePolicy();
        ModelPolicyJpaEntity entity = new ModelPolicyJpaMapper().toEntity(policy, tenantId.value());

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(ModelPolicyJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(ModelPolicyJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndDepartmentId(tenantId.value(), policy.departmentId()))
            .thenReturn(Optional.of(entity));

        Optional<ModelPolicy> result = adapter.findByTenantIdAndDepartmentId(tenantId, policy.departmentId());
        assertThat(result).isPresent();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantIdEDepartmentId_comTenantContext_filtroJaHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        ModelPolicy policy = samplePolicy();
        ModelPolicyJpaEntity entity = new ModelPolicyJpaMapper().toEntity(policy, tenantId.value());

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(ModelPolicyJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndDepartmentId(tenantId.value(), policy.departmentId()))
            .thenReturn(Optional.of(entity));

        Optional<ModelPolicy> result = adapter.findByTenantIdAndDepartmentId(tenantId, policy.departmentId());
        assertThat(result).isPresent();
        verify(session, never()).enableFilter(ModelPolicyJpaEntity.TENANT_FILTER_NAME);
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    private ModelPolicy samplePolicy() {
        return ModelPolicy.restore(
            UUID.randomUUID(),
            Set.of(UUID.randomUUID()),
            UUID.randomUUID(),
            Instant.now(),
            Instant.now()
        );
    }
}
