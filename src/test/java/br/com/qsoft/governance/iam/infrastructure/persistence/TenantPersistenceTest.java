package br.com.qsoft.governance.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantPersistenceTest {

    @Mock
    private TenantJpaRepository jpaRepository;

    @InjectMocks
    private TenantRepositoryAdapter adapter;

    @Test
    void deve_testarTenantJpaEntity_gettersESetters() {
        TenantJpaEntity entity = new TenantJpaEntity();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setSlug("qsoft");
        entity.setName("QSOFT Org");
        entity.setEmailDomain("qsoft.com");
        entity.setLogoUrl("http://logo.png");
        entity.setPrimaryColor("#FF0000");
        entity.setSecondaryColor("#00FF00");
        entity.setStatus(TenantStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getSlug()).isEqualTo("qsoft");
        assertThat(entity.getName()).isEqualTo("QSOFT Org");
        assertThat(entity.getEmailDomain()).isEqualTo("qsoft.com");
        assertThat(entity.getLogoUrl()).isEqualTo("http://logo.png");
        assertThat(entity.getPrimaryColor()).isEqualTo("#FF0000");
        assertThat(entity.getSecondaryColor()).isEqualTo("#00FF00");
        assertThat(entity.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deve_converterEntreTenantEEntity_comTenantJpaMapper() {
        TenantJpaMapper mapper = new TenantJpaMapper();
        TenantId tenantId = TenantId.random();
        Instant now = Instant.now();

        Tenant tenant = Tenant.restore(
            tenantId,
            "qsoft",
            "QSOFT Org",
            "qsoft.com",
            "http://logo.png",
            "#111111",
            "#222222",
            TenantStatus.ACTIVE,
            now,
            now
        );

        TenantJpaEntity entity = mapper.toEntity(tenant);
        assertThat(entity.getId()).isEqualTo(tenantId.value());
        assertThat(entity.getSlug()).isEqualTo("qsoft");

        Tenant restored = mapper.toDomain(entity);
        assertThat(restored.id()).isEqualTo(tenantId);
        assertThat(restored.slug()).isEqualTo("qsoft");
        assertThat(restored.name()).isEqualTo("QSOFT Org");
        assertThat(restored.emailDomain()).isEqualTo("qsoft.com");
        assertThat(restored.logoUrl()).isEqualTo("http://logo.png");
        assertThat(restored.primaryColor()).isEqualTo("#111111");
        assertThat(restored.secondaryColor()).isEqualTo("#222222");
        assertThat(restored.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void deve_salvarTenant() {
        Tenant tenant = createSampleTenant();
        when(jpaRepository.save(any(TenantJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Tenant saved = adapter.save(tenant);
        assertThat(saved.id()).isEqualTo(tenant.id());
        verify(jpaRepository).save(any(TenantJpaEntity.class));
    }

    @Test
    void deve_buscarPorSlug() {
        Tenant tenant = createSampleTenant();
        TenantJpaEntity entity = new TenantJpaMapper().toEntity(tenant);

        when(jpaRepository.findBySlug("qsoft")).thenReturn(Optional.of(entity));

        Optional<Tenant> result = adapter.findBySlug("qsoft");
        assertThat(result).isPresent();
        assertThat(result.get().slug()).isEqualTo("qsoft");
    }

    @Test
    void deve_buscarPorId() {
        Tenant tenant = createSampleTenant();
        TenantJpaEntity entity = new TenantJpaMapper().toEntity(tenant);

        when(jpaRepository.findById(tenant.id().value())).thenReturn(Optional.of(entity));

        Optional<Tenant> result = adapter.findById(tenant.id());
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(tenant.id());
    }

    @Test
    void deve_verificarExistsBySlug() {
        when(jpaRepository.existsBySlug("qsoft")).thenReturn(true);
        assertThat(adapter.existsBySlug("qsoft")).isTrue();
    }

    @Test
    void deve_retornarEmpty_quando_findByEmailDomainNulo() {
        assertThat(adapter.findByEmailDomain(null)).isEmpty();
    }

    @Test
    void deve_retornarEmpty_quando_findByEmailDomainVazio() {
        assertThat(adapter.findByEmailDomain("")).isEmpty();
    }

    @Test
    void deve_retornarEmpty_quando_findByEmailDomainEmBranco() {
        assertThat(adapter.findByEmailDomain("   ")).isEmpty();
    }

    @Test
    void deve_buscarPorEmailDomainValido() {
        Tenant tenant = createSampleTenant();
        TenantJpaEntity entity = new TenantJpaMapper().toEntity(tenant);

        when(jpaRepository.findByEmailDomain("qsoft.com")).thenReturn(Optional.of(entity));

        Optional<Tenant> result = adapter.findByEmailDomain("  QSOFT.COM  ");
        assertThat(result).isPresent();
        assertThat(result.get().emailDomain()).isEqualTo("qsoft.com");
    }

    @Test
    void deve_retornarFalse_quando_existsByEmailDomainNulo() {
        assertThat(adapter.existsByEmailDomain(null)).isFalse();
    }

    @Test
    void deve_retornarFalse_quando_existsByEmailDomainVazio() {
        assertThat(adapter.existsByEmailDomain("")).isFalse();
    }

    @Test
    void deve_retornarFalse_quando_existsByEmailDomainEmBranco() {
        assertThat(adapter.existsByEmailDomain("   ")).isFalse();
    }

    @Test
    void deve_verificarExistsByEmailDomainValido() {
        when(jpaRepository.existsByEmailDomain("qsoft.com")).thenReturn(true);
        assertThat(adapter.existsByEmailDomain("  QSOFT.COM  ")).isTrue();
    }

    private Tenant createSampleTenant() {
        return Tenant.restore(
            TenantId.random(),
            "qsoft",
            "QSOFT Org",
            "qsoft.com",
            null,
            null,
            null,
            TenantStatus.ACTIVE,
            Instant.now(),
            Instant.now()
        );
    }
}
