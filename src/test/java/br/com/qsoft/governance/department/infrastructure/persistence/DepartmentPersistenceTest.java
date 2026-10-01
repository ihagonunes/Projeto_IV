package br.com.qsoft.governance.department.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class DepartmentPersistenceTest {

    @Mock
    private DepartmentJpaRepository jpaRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DepartmentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_testarDepartmentJpaEntity_gettersESetters() {
        DepartmentJpaEntity entity = new DepartmentJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setName("Tecnologia");
        entity.setDescription("Depto de TI");
        entity.setLeaderUserId(leaderId);
        entity.setMonthlyBudget(new BigDecimal("50000.00"));
        entity.setStatus(DepartmentStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getName()).isEqualTo("Tecnologia");
        assertThat(entity.getDescription()).isEqualTo("Depto de TI");
        assertThat(entity.getLeaderUserId()).isEqualTo(leaderId);
        assertThat(entity.getMonthlyBudget()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(entity.getStatus()).isEqualTo(DepartmentStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deve_converterEntreDepartmentEEntity_comDepartmentJpaMapper() {
        DepartmentJpaMapper mapper = new DepartmentJpaMapper();
        TenantId tenantId = TenantId.random();
        UUID id = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Instant now = Instant.now();

        Department department = Department.restore(
            id,
            tenantId,
            "Engenharia",
            "Engenharia de Software",
            leaderId,
            new BigDecimal("75000.00"),
            DepartmentStatus.ACTIVE,
            now,
            now
        );

        DepartmentJpaEntity entity = mapper.toEntity(department);
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getName()).isEqualTo("Engenharia");
        assertThat(entity.getDescription()).isEqualTo("Engenharia de Software");
        assertThat(entity.getLeaderUserId()).isEqualTo(leaderId);
        assertThat(entity.getMonthlyBudget()).isEqualTo(new BigDecimal("75000.00"));
        assertThat(entity.getStatus()).isEqualTo(DepartmentStatus.ACTIVE);

        Department restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.name()).isEqualTo("Engenharia");
        assertThat(restored.description()).isEqualTo("Engenharia de Software");
        assertThat(restored.leaderUserId()).isEqualTo(leaderId);
        assertThat(restored.monthlyBudget()).isEqualTo(new BigDecimal("75000.00"));
        assertThat(restored.status()).isEqualTo(DepartmentStatus.ACTIVE);
    }

    @Test
    void deve_salvarDepartment() {
        Department department = sampleDepartment();
        when(jpaRepository.save(any(DepartmentJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Department saved = adapter.save(department);
        assertThat(saved.id()).isEqualTo(department.id());
        verify(jpaRepository).save(any(DepartmentJpaEntity.class));
    }

    @Test
    void deve_buscarPorTenantIdEId_semTenantContext() {
        Department department = sampleDepartment();
        DepartmentJpaEntity entity = new DepartmentJpaMapper().toEntity(department);

        when(jpaRepository.findByTenantIdAndId(department.tenantId().value(), department.id()))
            .thenReturn(Optional.of(entity));

        Optional<Department> result = adapter.findByTenantIdAndId(department.tenantId(), department.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(department.id());
    }

    @Test
    void deve_buscarPorTenantIdEId_comTenantContext_filtroNaoHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Department department = sampleDepartment(tenantId);
        DepartmentJpaEntity entity = new DepartmentJpaMapper().toEntity(department);

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(DepartmentJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(DepartmentJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndId(tenantId.value(), department.id()))
            .thenReturn(Optional.of(entity));

        Optional<Department> result = adapter.findByTenantIdAndId(tenantId, department.id());
        assertThat(result).isPresent();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantIdEId_comTenantContext_filtroJaHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Department department = sampleDepartment(tenantId);
        DepartmentJpaEntity entity = new DepartmentJpaMapper().toEntity(department);

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(DepartmentJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndId(tenantId.value(), department.id()))
            .thenReturn(Optional.of(entity));

        Optional<Department> result = adapter.findByTenantIdAndId(tenantId, department.id());
        assertThat(result).isPresent();
        verify(session, never()).enableFilter(DepartmentJpaEntity.TENANT_FILTER_NAME);
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantId() {
        Department department = sampleDepartment();
        DepartmentJpaEntity entity = new DepartmentJpaMapper().toEntity(department);

        when(jpaRepository.findByTenantId(department.tenantId().value()))
            .thenReturn(List.of(entity));

        List<Department> result = adapter.findByTenantId(department.tenantId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(department.id());
    }

    @Test
    void deve_verificarExistsByTenantIdAndName() {
        TenantId tenantId = TenantId.random();
        when(jpaRepository.existsByTenantIdAndName(tenantId.value(), "Engenharia")).thenReturn(true);

        assertThat(adapter.existsByTenantIdAndName(tenantId, "Engenharia")).isTrue();
    }

    @Test
    void deve_verificarExistsByTenantIdAndNameAndIdNot() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        when(jpaRepository.existsByTenantIdAndNameAndIdNot(tenantId.value(), "Engenharia", deptId))
            .thenReturn(true);

        assertThat(adapter.existsByTenantIdAndNameAndIdNot(tenantId, "Engenharia", deptId)).isTrue();
    }

    private Department sampleDepartment() {
        return sampleDepartment(TenantId.random());
    }

    private Department sampleDepartment(TenantId tenantId) {
        return Department.restore(
            UUID.randomUUID(),
            tenantId,
            "Engenharia",
            "Engenharia de Software",
            UUID.randomUUID(),
            new BigDecimal("50000.00"),
            DepartmentStatus.ACTIVE,
            Instant.now(),
            Instant.now()
        );
    }
}
