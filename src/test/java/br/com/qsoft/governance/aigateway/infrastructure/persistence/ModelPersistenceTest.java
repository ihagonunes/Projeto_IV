package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
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
class ModelPersistenceTest {

    @Mock
    private ModelJpaRepository jpaRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ModelRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_testarModelJpaEntity_gettersESetters() {
        ModelJpaEntity entity = new ModelJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setProviderId(providerId);
        entity.setName("GPT-4o");
        entity.setStatus(ModelStatus.AVAILABLE);
        entity.setInputPricePer1k(new BigDecimal("0.005"));
        entity.setOutputPricePer1k(new BigDecimal("0.015"));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getProviderId()).isEqualTo(providerId);
        assertThat(entity.getName()).isEqualTo("GPT-4o");
        assertThat(entity.getStatus()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(entity.getInputPricePer1k()).isEqualTo(new BigDecimal("0.005"));
        assertThat(entity.getOutputPricePer1k()).isEqualTo(new BigDecimal("0.015"));
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deve_converterEntreModelEEntity_comModelJpaMapper() {
        ModelJpaMapper mapper = new ModelJpaMapper();
        TenantId tenantId = TenantId.random();
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Instant now = Instant.now();

        Model model = Model.restore(
            id,
            tenantId,
            providerId,
            "GPT-4o",
            null,
            ModelStatus.AVAILABLE,
            new BigDecimal("0.005"),
            new BigDecimal("0.015"),
            now,
            now
        );

        ModelJpaEntity entity = mapper.toEntity(model);
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getProviderId()).isEqualTo(providerId);
        assertThat(entity.getName()).isEqualTo("GPT-4o");

        Model restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.providerId()).isEqualTo(providerId);
        assertThat(restored.name()).isEqualTo("GPT-4o");
        assertThat(restored.status()).isEqualTo(ModelStatus.AVAILABLE);
    }

    @Test
    void deve_salvarModel() {
        Model model = sampleModel();
        when(jpaRepository.save(any(ModelJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Model saved = adapter.save(model);
        assertThat(saved.id()).isEqualTo(model.id());
        verify(jpaRepository).save(any(ModelJpaEntity.class));
    }

    @Test
    void deve_buscarPorTenantIdEId_semTenantContext() {
        Model model = sampleModel();
        ModelJpaEntity entity = new ModelJpaMapper().toEntity(model);

        when(jpaRepository.findByTenantIdAndId(model.tenantId().value(), model.id()))
            .thenReturn(Optional.of(entity));

        Optional<Model> result = adapter.findByTenantIdAndId(model.tenantId(), model.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(model.id());
    }

    @Test
    void deve_buscarPorTenantIdEId_comTenantContext() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Model model = sampleModel(tenantId);
        ModelJpaEntity entity = new ModelJpaMapper().toEntity(model);

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(ModelJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(ModelJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndId(tenantId.value(), model.id()))
            .thenReturn(Optional.of(entity));

        Optional<Model> result = adapter.findByTenantIdAndId(tenantId, model.id());
        assertThat(result).isPresent();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantId() {
        Model model = sampleModel();
        ModelJpaEntity entity = new ModelJpaMapper().toEntity(model);

        when(jpaRepository.findByTenantId(model.tenantId().value()))
            .thenReturn(List.of(entity));

        List<Model> result = adapter.findByTenantId(model.tenantId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(model.id());
    }

    @Test
    void deve_retornarListaVazia_quando_findByTenantIdAndIdInComCollectionNulaOuVazia() {
        TenantId tenantId = TenantId.random();

        assertThat(adapter.findByTenantIdAndIdIn(tenantId, null)).isEmpty();
        assertThat(adapter.findByTenantIdAndIdIn(tenantId, List.of())).isEmpty();
    }

    @Test
    void deve_buscarPorTenantIdEIdIn_comListaPreenchida() {
        Model model = sampleModel();
        ModelJpaEntity entity = new ModelJpaMapper().toEntity(model);
        List<UUID> ids = List.of(model.id());

        when(jpaRepository.findByTenantIdAndIdIn(model.tenantId().value(), ids))
            .thenReturn(List.of(entity));

        List<Model> result = adapter.findByTenantIdAndIdIn(model.tenantId(), ids);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(model.id());
    }

    @Test
    void deve_verificarExistsByTenantIdAndName() {
        TenantId tenantId = TenantId.random();
        when(jpaRepository.existsByTenantIdAndName(tenantId.value(), "GPT-4o")).thenReturn(true);

        assertThat(adapter.existsByTenantIdAndName(tenantId, "GPT-4o")).isTrue();
    }

    @Test
    void deve_retornarImediatamente_quando_tenantContextForNulo() {
        TenantContextHolder.clear();

        adapter.applyTenantFilter();
    }

    @Test
    void deve_habilitarEConfigurarFiltro_quando_filtroEstiverNulo() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Session sessionMock = mock(Session.class);
        Filter filterMock = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(sessionMock);
        when(sessionMock.getEnabledFilter(ModelJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(sessionMock.enableFilter(ModelJpaEntity.TENANT_FILTER_NAME)).thenReturn(filterMock);

        adapter.applyTenantFilter();

        verify(sessionMock).enableFilter(ModelJpaEntity.TENANT_FILTER_NAME);
        verify(filterMock).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_usarFiltroExistente_quando_filtroJaEstiverHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Session sessionMock = mock(Session.class);
        Filter filterMock = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(sessionMock);
        when(sessionMock.getEnabledFilter(ModelJpaEntity.TENANT_FILTER_NAME)).thenReturn(filterMock);

        adapter.applyTenantFilter();

        verify(sessionMock, org.mockito.Mockito.never()).enableFilter(ModelJpaEntity.TENANT_FILTER_NAME);
        verify(filterMock).setParameter("tenantId", tenantId.value());
    }

    private Model sampleModel() {
        return sampleModel(TenantId.random());
    }

    private Model sampleModel(TenantId tenantId) {
        return Model.restore(
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID(),
            "GPT-4o",
            null,
            ModelStatus.AVAILABLE,
            new BigDecimal("0.005"),
            new BigDecimal("0.015"),
            Instant.now(),
            Instant.now()
        );
    }
}
