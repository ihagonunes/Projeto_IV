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

class UpdateTenantTest {

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

    private final UpdateTenant updateTenant = new UpdateTenant(tenantRepository);
    private final GetCurrentTenant getCurrentTenant = new GetCurrentTenant(tenantRepository);

    private Tenant tenant(String slug, String name) {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(id, slug, name, TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenants.put(id, tenant);
        return tenant;
    }

    @Test
    void shouldUpdateTenantNameKeepingSlug() {
        Tenant tenant = tenant("qsoft", "QSOFT Antigo");

        Tenant updated = updateTenant.update(new UpdateTenantCommand(tenant.id(), "QSOFT Novo"));

        assertThat(updated.name()).isEqualTo("QSOFT Novo");
        assertThat(updated.slug()).isEqualTo("qsoft");
        assertThat(tenantRepository.findById(tenant.id()).orElseThrow().name())
            .isEqualTo("QSOFT Novo");
    }

    @Test
    void shouldTrimName() {
        Tenant tenant = tenant("qsoft", "QSOFT");

        assertThat(updateTenant.update(new UpdateTenantCommand(tenant.id(), "  QSOFT SA  ")).name())
            .isEqualTo("QSOFT SA");
    }

    @Test
    void shouldRejectBlankName() {
        Tenant tenant = tenant("qsoft", "QSOFT");

        assertThatThrownBy(() -> updateTenant.update(new UpdateTenantCommand(tenant.id(), "  ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUnknownTenant() {
        assertThatThrownBy(() -> updateTenant.update(
            new UpdateTenantCommand(TenantId.random(), "X")))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldReturnCurrentTenant() {
        Tenant tenant = tenant("qsoft", "QSOFT");

        assertThat(getCurrentTenant.current(tenant.id()).slug()).isEqualTo("qsoft");
    }

    @Test
    void shouldRejectUnknownCurrentTenant() {
        assertThatThrownBy(() -> getCurrentTenant.current(TenantId.random()))
            .isInstanceOf(TenantNotFoundException.class);
    }
}
