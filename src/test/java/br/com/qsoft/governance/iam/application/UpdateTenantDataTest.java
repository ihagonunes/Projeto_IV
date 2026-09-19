package br.com.qsoft.governance.iam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateTenantDataTest {

    private InMemoryTenantRepository tenantRepository;
    private UpdateTenantData updateTenantData;

    @BeforeEach
    void setUp() {
        tenantRepository = new InMemoryTenantRepository();
        updateTenantData = new UpdateTenantData(tenantRepository);
    }

    @Test
    void shouldUpdateTenantName() {
        TenantId tenantId = TenantId.random();
        Tenant tenant = Tenant.create(tenantId, "test-tenant", "Old Name");
        tenantRepository.save(tenant);

        Tenant updated = updateTenantData.updateData(new UpdateTenantDataCommand(tenantId, "New Name"));

        assertEquals("New Name", updated.name());
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
