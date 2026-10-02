package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
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
class ProviderPersistenceTest {

    @Mock
    private ProviderJpaRepository jpaRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProviderRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deve_testarProviderJpaEntity_gettersESetters() {
        ProviderJpaEntity entity = new ProviderJpaEntity();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setName("OpenAI");
        entity.setStatus(ProviderStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getName()).isEqualTo("OpenAI");
        assertThat(entity.getStatus()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deve_converterEntreProviderEEntity_comProviderJpaMapper() {
        ProviderJpaMapper mapper = new ProviderJpaMapper();
        TenantId tenantId = TenantId.random();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Provider provider = Provider.restore(
            id,
            tenantId,
            "OpenAI",
            ProviderStatus.ACTIVE,
            now,
            now
        );

        ProviderJpaEntity entity = mapper.toEntity(provider);
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getName()).isEqualTo("OpenAI");
        assertThat(entity.getStatus()).isEqualTo(ProviderStatus.ACTIVE);

        Provider restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.tenantId()).isEqualTo(tenantId);
        assertThat(restored.name()).isEqualTo("OpenAI");
        assertThat(restored.status()).isEqualTo(ProviderStatus.ACTIVE);
    }

    @Test
    void deve_salvarProvider() {
        Provider provider = sampleProvider();
        when(jpaRepository.save(any(ProviderJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Provider saved = adapter.save(provider);
        assertThat(saved.id()).isEqualTo(provider.id());
        verify(jpaRepository).save(any(ProviderJpaEntity.class));
    }

    @Test
    void deve_buscarPorTenantIdEId_semTenantContext() {
        Provider provider = sampleProvider();
        ProviderJpaEntity entity = new ProviderJpaMapper().toEntity(provider);

        when(jpaRepository.findByTenantIdAndId(provider.tenantId().value(), provider.id()))
            .thenReturn(Optional.of(entity));

        Optional<Provider> result = adapter.findByTenantIdAndId(provider.tenantId(), provider.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(provider.id());
    }

    @Test
    void deve_buscarPorTenantIdEId_comTenantContext_filtroNaoHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Provider provider = sampleProvider(tenantId);
        ProviderJpaEntity entity = new ProviderJpaMapper().toEntity(provider);

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(ProviderJpaEntity.TENANT_FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(ProviderJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndId(tenantId.value(), provider.id()))
            .thenReturn(Optional.of(entity));

        Optional<Provider> result = adapter.findByTenantIdAndId(tenantId, provider.id());
        assertThat(result).isPresent();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantIdEId_comTenantContext_filtroJaHabilitado() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        Provider provider = sampleProvider(tenantId);
        ProviderJpaEntity entity = new ProviderJpaMapper().toEntity(provider);

        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.getEnabledFilter(ProviderJpaEntity.TENANT_FILTER_NAME)).thenReturn(filter);

        when(jpaRepository.findByTenantIdAndId(tenantId.value(), provider.id()))
            .thenReturn(Optional.of(entity));

        Optional<Provider> result = adapter.findByTenantIdAndId(tenantId, provider.id());
        assertThat(result).isPresent();
        verify(filter).setParameter("tenantId", tenantId.value());
    }

    @Test
    void deve_buscarPorTenantId() {
        Provider provider = sampleProvider();
        ProviderJpaEntity entity = new ProviderJpaMapper().toEntity(provider);

        when(jpaRepository.findByTenantId(provider.tenantId().value()))
            .thenReturn(List.of(entity));

        List<Provider> result = adapter.findByTenantId(provider.tenantId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(provider.id());
    }

    @Test
    void deve_verificarExistsByTenantIdAndName() {
        TenantId tenantId = TenantId.random();
        when(jpaRepository.existsByTenantIdAndName(tenantId.value(), "OpenAI")).thenReturn(true);

        assertThat(adapter.existsByTenantIdAndName(tenantId, "OpenAI")).isTrue();
    }

    private Provider sampleProvider() {
        return sampleProvider(TenantId.random());
    }

    private Provider sampleProvider(TenantId tenantId) {
        return Provider.restore(
            UUID.randomUUID(),
            tenantId,
            "OpenAI",
            ProviderStatus.ACTIVE,
            Instant.now(),
            Instant.now()
        );
    }
}
