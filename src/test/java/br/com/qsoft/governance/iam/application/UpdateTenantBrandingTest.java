package br.com.qsoft.governance.iam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateTenantBrandingTest {

    private InMemoryTenantRepository tenantRepository;
    private UpdateTenantBranding updateTenantBranding;

    @BeforeEach
    void setUp() {
        tenantRepository = new InMemoryTenantRepository();
        updateTenantBranding = new UpdateTenantBranding(tenantRepository);
    }

    @Test
    void shouldUpdateTenantBranding() {
        TenantId tenantId = TenantId.random();
        Tenant tenant = Tenant.create(tenantId, "test-tenant", "Test Tenant");
        tenantRepository.save(tenant);

        Tenant updated = updateTenantBranding.updateBranding(new UpdateTenantBrandingCommand(
            tenantId,
            "https://logo.png",
            "#000000",
            "#FFFFFF"
        ));

        assertEquals("https://logo.png", updated.logoUrl());
        assertEquals("#000000", updated.primaryColor());
        assertEquals("#FFFFFF", updated.secondaryColor());
    }

    static class InMemoryTenantRepository implements TenantRepository {
        private final java.util.Map<TenantId, Tenant> tenants = new java.util.HashMap<>();

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
            return tenants.values().stream()
                .filter(t -> t.slug().equalsIgnoreCase(slug))
                .findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return findBySlug(slug).isPresent();
        }

        public java.util.List<Tenant> findAll() {
            return java.util.List.copyOf(tenants.values());
        }
    }
}
