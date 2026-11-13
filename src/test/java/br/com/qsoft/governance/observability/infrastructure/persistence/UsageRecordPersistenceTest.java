package br.com.qsoft.governance.observability.infrastructure.persistence;

import br.com.qsoft.governance.observability.application.port.UsageRecordRepository.UsageRecordFilter;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import br.com.qsoft.governance.observability.application.UsageVisibilityDeniedException;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsageRecordPersistenceTest {

    @Mock
    private UsageRecordJpaRepository jpaRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UsageRecordRepositoryAdapter repositoryAdapter;

    private final UsageRecordJpaMapper mapper = new UsageRecordJpaMapper();

    @Test
    void specificationUnionsDepartmentScopeWithSelfAndDeniesNoVisibility() {
        UsageRecord record = criarRecordExemplo();
        var self = br.com.qsoft.governance.sharedkernel.id.UserId.random();
        var departments = java.util.Set.of(java.util.UUID.randomUUID());
        var scope = new VisibilityScope.Departments(departments, self);
        Specification<UsageRecordJpaEntity> departmentSpec = specification(record, scope);
        Specification<UsageRecordJpaEntity> selfSpec = specification(record, new VisibilityScope.SelfOnly(self));
        Specification<UsageRecordJpaEntity> denied = specification(record, new VisibilityScope.None());

        Root<UsageRecordJpaEntity> root = mock();
        CriteriaBuilder builder = mock();
        Path<java.util.UUID> tenantPath = mock();
        Path<java.util.UUID> departmentPath = mock();
        Path<java.util.UUID> userPath = mock();
        Predicate tenantPredicate = mock();
        Predicate departmentPredicate = mock();
        Predicate userPredicate = mock();
        Predicate union = mock();
        when(root.<java.util.UUID>get("tenantId")).thenReturn(tenantPath);
        when(root.<java.util.UUID>get("departmentId")).thenReturn(departmentPath);
        when(root.<java.util.UUID>get("userId")).thenReturn(userPath);
        when(builder.equal(tenantPath, record.tenantId().value())).thenReturn(tenantPredicate);
        when(departmentPath.in(departments)).thenReturn(departmentPredicate);
        when(builder.equal(userPath, self.value())).thenReturn(userPredicate);
        when(builder.or(departmentPredicate, userPredicate)).thenReturn(union);

        departmentSpec.toPredicate(root, null, builder);
        selfSpec.toPredicate(root, null, builder);
        assertThatThrownBy(() -> denied.toPredicate(root, null, builder))
            .isInstanceOf(UsageVisibilityDeniedException.class);

        ArgumentCaptor<Predicate[]> predicates = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder, org.mockito.Mockito.times(2)).and(predicates.capture());
        assertThat(predicates.getAllValues().getFirst()).containsExactly(tenantPredicate, union);
        assertThat(predicates.getAllValues().getLast()).containsExactly(tenantPredicate, userPredicate);
    }

    private Specification<UsageRecordJpaEntity> specification(UsageRecord record, VisibilityScope visibility) {
        ArgumentCaptor<Specification<UsageRecordJpaEntity>> spec = ArgumentCaptor.captor();
        when(jpaRepository.findAll(spec.capture(), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        repositoryAdapter.search(new UsageRecordFilter(record.tenantId(), visibility, null, null, null, null), 0, 10);
        return spec.getValue();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repositoryAdapter, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- Entity Tests ---

    @Test
    void deve_testar_getters_e_setters_do_usage_record_jpa_entity() {
        UsageRecordJpaEntity entity = new UsageRecordJpaEntity();
        UUID id = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.001500");
        Instant now = Instant.now();

        entity.setId(id);
        entity.setRequestId(requestId);
        entity.setTenantId(tenantId);
        entity.setDepartmentId(departmentId);
        entity.setUserId(userId);
        entity.setModelId(modelId);
        entity.setProviderId(providerId);
        entity.setInputTokens(100L);
        entity.setOutputTokens(200L);
        entity.setEstimatedCost(cost);
        entity.setLatencyMs(350L);
        entity.setStatus("COMPLETED");
        entity.setCreatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getRequestId()).isEqualTo(requestId);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getDepartmentId()).isEqualTo(departmentId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getModelId()).isEqualTo(modelId);
        assertThat(entity.getProviderId()).isEqualTo(providerId);
        assertThat(entity.getInputTokens()).isEqualTo(100L);
        assertThat(entity.getOutputTokens()).isEqualTo(200L);
        assertThat(entity.getEstimatedCost()).isEqualTo(cost);
        assertThat(entity.getLatencyMs()).isEqualTo(350L);
        assertThat(entity.getStatus()).isEqualTo("COMPLETED");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(UsageRecordJpaEntity.TENANT_FILTER_NAME).isEqualTo("usageRecordTenantFilter");
    }

    // --- Mapper Tests ---

    @Test
    void deve_mapear_usage_record_para_entidade_e_de_volta() {
        UUID id = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        TenantId tenantId = TenantId.random();
        UUID departmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.005");
        Instant now = Instant.now();

        UsageRecord domain = UsageRecord.restore(
            id, requestId, tenantId, departmentId, userId,
            modelId, providerId, 150L, 250L, cost, 450L, "SUCCESS", now
        );

        UsageRecordJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getRequestId()).isEqualTo(requestId);
        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getDepartmentId()).isEqualTo(departmentId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getModelId()).isEqualTo(modelId);
        assertThat(entity.getProviderId()).isEqualTo(providerId);
        assertThat(entity.getInputTokens()).isEqualTo(150L);
        assertThat(entity.getOutputTokens()).isEqualTo(250L);
        assertThat(entity.getEstimatedCost()).isEqualTo(cost);
        assertThat(entity.getLatencyMs()).isEqualTo(450L);
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getCreatedAt()).isEqualTo(now);

        UsageRecord restoredDomain = mapper.toDomain(entity);

        assertThat(restoredDomain.id()).isEqualTo(id);
        assertThat(restoredDomain.requestId()).isEqualTo(requestId);
        assertThat(restoredDomain.tenantId()).isEqualTo(tenantId);
        assertThat(restoredDomain.departmentId()).isEqualTo(departmentId);
        assertThat(restoredDomain.userId()).isEqualTo(userId);
        assertThat(restoredDomain.modelId()).isEqualTo(modelId);
        assertThat(restoredDomain.providerId()).isEqualTo(providerId);
        assertThat(restoredDomain.inputTokens()).isEqualTo(150L);
        assertThat(restoredDomain.outputTokens()).isEqualTo(250L);
        assertThat(restoredDomain.estimatedCost()).isEqualTo(cost);
        assertThat(restoredDomain.latencyMs()).isEqualTo(450L);
        assertThat(restoredDomain.status()).isEqualTo("SUCCESS");
        assertThat(restoredDomain.createdAt()).isEqualTo(now);
    }

    // --- Adapter Tests ---

    @Test
    void deve_salvar_usage_record_com_sucesso() {
        UsageRecord record = criarRecordExemplo();

        when(jpaRepository.save(any(UsageRecordJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsageRecord saved = repositoryAdapter.save(record);

        assertThat(saved.id()).isEqualTo(record.id());
        assertThat(saved.requestId()).isEqualTo(record.requestId());
        verify(jpaRepository).save(any(UsageRecordJpaEntity.class));
    }

    @Test
    void deve_verificar_existencia_por_request_id() {
        UUID requestId = UUID.randomUUID();

        when(jpaRepository.existsByRequestId(requestId)).thenReturn(true);

        boolean exists = repositoryAdapter.existsByRequestId(requestId);

        assertThat(exists).isTrue();
        verify(jpaRepository).existsByRequestId(requestId);
    }

    @Test
    void deve_buscar_usage_records_com_paginacao_e_filtro() {
        UsageRecord record = criarRecordExemplo();
        UsageRecordJpaEntity entity = mapper.toEntity(record);

        when(jpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        UsageRecordFilter filter = new UsageRecordFilter(
            record.tenantId(),
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(),
            record.departmentId(),
            record.userId(),
            Instant.now().minusSeconds(3600),
            Instant.now()
        );

        PageResult<UsageRecord> result = repositoryAdapter.search(filter, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        verify(jpaRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    // --- Specification Branch Tests ---

    @Test
    void deve_construir_specification_com_filtros_completos() {
        UsageRecord record = criarRecordExemplo();
        UsageRecordFilter filter = new UsageRecordFilter(
            record.tenantId(),
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(),
            record.departmentId(),
            record.userId(),
            Instant.now().minusSeconds(3600),
            Instant.now()
        );

        when(jpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        repositoryAdapter.search(filter, 0, 10);

        ArgumentCaptor<Specification<UsageRecordJpaEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(jpaRepository).findAll(specCaptor.capture(), any(PageRequest.class));

        Specification<UsageRecordJpaEntity> spec = specCaptor.getValue();
        assertThat(spec).isNotNull();

        Root<UsageRecordJpaEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get(any(String.class))).thenReturn(path);
        when(builder.equal(any(), any())).thenReturn(predicate);
        when(builder.greaterThanOrEqualTo(any(), any(Instant.class))).thenReturn(predicate);
        when(builder.lessThanOrEqualTo(any(), any(Instant.class))).thenReturn(predicate);
        when(builder.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate resultPredicate = spec.toPredicate(root, query, builder);
        assertThat(resultPredicate).isNotNull();
    }

    @Test
    void deve_construir_specification_com_filtros_nulos() {
        UsageRecordFilter filter = new UsageRecordFilter(
            TenantId.random(),
            new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.TenantWide(),
            null,
            null,
            null,
            null
        );

        when(jpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        repositoryAdapter.search(filter, 0, 10);

        ArgumentCaptor<Specification<UsageRecordJpaEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(jpaRepository).findAll(specCaptor.capture(), any(PageRequest.class));

        Specification<UsageRecordJpaEntity> spec = specCaptor.getValue();
        assertThat(spec).isNotNull();

        Root<UsageRecordJpaEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("tenantId")).thenReturn(path);
        when(builder.equal(any(), any())).thenReturn(predicate);
        when(builder.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate resultPredicate = spec.toPredicate(root, query, builder);
        assertThat(resultPredicate).isNotNull();
    }

    // --- applyTenantFilter Branch Tests ---

    @Test
    void deve_retornar_sem_aplicar_filtro_quando_tenant_context_estiver_nulo() {
        TenantContextHolder.clear();

        repositoryAdapter.applyTenantFilter();

        // Nenhuma interação com entityManager deve ocorrer
    }

    @Test
    void deve_habilitar_e_configurar_filtro_quando_filtro_estiver_nulo() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, null));

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(UsageRecordJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(UsageRecordJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        repositoryAdapter.applyTenantFilter();

        verify(session).enableFilter(UsageRecordJpaEntity.TENANT_FILTER_NAME);
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_configurar_filtro_quando_filtro_ja_estiver_habilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, null));

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(UsageRecordJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        repositoryAdapter.applyTenantFilter();

        verify(filter).setParameter("tenantId", tenantId.value());
    }

    // --- Helper Methods ---

    @Test
    void deve_inserir_idempotente_com_on_conflict_e_tenant_id() {
        UsageRecord record = criarRecordExemplo();
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

        boolean inserted = repositoryAdapter.insertIfAbsent(record);

        assertThat(inserted).isTrue();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertThat(sql.getValue())
            .contains("ON CONFLICT (request_id) DO NOTHING")
            .contains("tenant_id");
        assertThat(args.getValue()[2]).isEqualTo(record.tenantId().value());
    }

    @Test
    void deve_retornar_false_quando_request_id_ja_existir() {
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(0);

        assertThat(repositoryAdapter.insertIfAbsent(criarRecordExemplo())).isFalse();
    }

    private UsageRecord criarRecordExemplo() {
        return UsageRecord.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TenantId.random(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            100L,
            200L,
            new BigDecimal("0.002000"),
            150L,
            "SUCCESS",
            Instant.now()
        );
    }
}
