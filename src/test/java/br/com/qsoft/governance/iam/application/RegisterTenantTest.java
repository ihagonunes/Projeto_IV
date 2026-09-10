package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegisterTenantTest {

    private final Map<String, Tenant> bySlug = new LinkedHashMap<>();

    private TenantRepository repository() {
        return new TenantRepository() {
            @Override
            public Tenant save(Tenant tenant) {
                bySlug.put(tenant.slug(), tenant);
                return tenant;
            }

            @Override
            public Optional<Tenant> findBySlug(String slug) {
                return Optional.ofNullable(bySlug.get(slug));
            }

            @Override
            public Optional<Tenant> findById(TenantId id) {
                return bySlug.values().stream()
                    .filter(t -> t.id().equals(id))
                    .findFirst();
            }

            @Override
            public boolean existsBySlug(String slug) {
                return bySlug.containsKey(slug);
            }
        };
    }

    @Test
    void shouldRegisterTenantAsActive() {
        RegisterTenant useCase = new RegisterTenant(repository());

        Tenant tenant = useCase.register(new RegisterTenantCommand("qsoft", "QSOFT"));

        assertThat(tenant).isNotNull();
        assertThat(tenant.id()).isNotNull();
        assertThat(tenant.slug()).isEqualTo("qsoft");
        assertThat(tenant.name()).isEqualTo("QSOFT");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateSlug() {
        RegisterTenant useCase = new RegisterTenant(repository());
        useCase.register(new RegisterTenantCommand("qsoft", "QSOFT"));

        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand("qsoft", "Outra")))
            .isInstanceOf(TenantSlugAlreadyExistsException.class);
    }

    @Test
    void shouldRejectBlankSlug() {
        RegisterTenant useCase = new RegisterTenant(repository());

        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand("  ", "QSOFT")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
