package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateTenantBrandingTest {

    private final Map<TenantId, Tenant> tenants = new LinkedHashMap<>();

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant tenant) {
            tenants.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenants.get(id));
        }

        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return tenants.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenants.values().stream().anyMatch(t -> t.slug().equals(slug));
        }
    };

    private final UpdateTenantBranding useCase = new UpdateTenantBranding(tenantRepository);

    private Tenant tenant() {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(id, "qsoft", "QSOFT", "qsoft.com",
            TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenants.put(id, tenant);
        return tenant;
    }

    @Test
    void shouldUpdateBrandingNormalizingColors() {
        Tenant tenant = tenant();

        Tenant updated = useCase.updateBranding(new UpdateTenantBrandingCommand(
            tenant.id(), "https://cdn.example.com/logo.png", "#1a2b3c", "#ffffff"));

        assertThat(updated.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(updated.primaryColor()).isEqualTo("#1A2B3C");
        assertThat(updated.secondaryColor()).isEqualTo("#FFFFFF");
        assertThat(tenantRepository.findById(tenant.id()).orElseThrow().primaryColor())
            .isEqualTo("#1A2B3C");
    }

    @Test
    void shouldClearBrandingWhenFieldsAreNullOrBlank() {
        Tenant tenant = tenant();
        useCase.updateBranding(new UpdateTenantBrandingCommand(
            tenant.id(), "https://x/logo.png", "#000000", "#111111"));

        Tenant cleared = useCase.updateBranding(new UpdateTenantBrandingCommand(
            tenant.id(), "   ", "", null));

        assertThat(cleared.logoUrl()).isNull();
        assertThat(cleared.primaryColor()).isNull();
        assertThat(cleared.secondaryColor()).isNull();
    }

    @Test
    void shouldRejectInvalidColorFormat() {
        Tenant tenant = tenant();

        assertThatThrownBy(() -> useCase.updateBranding(new UpdateTenantBrandingCommand(
            tenant.id(), null, "azul", null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> useCase.updateBranding(new UpdateTenantBrandingCommand(
            TenantId.random(), null, "#000000", null)))
            .isInstanceOf(TenantNotFoundException.class);
    }
}
