package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantDomainRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.rbac.ProvisionDefaultRoles;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegisterTenantTest {

    private final Map<String, Tenant> bySlug = new LinkedHashMap<>();
    private final List<Role> roles = new ArrayList<>();

    private final RoleRepository roleRepository = new RoleRepository() {
        @Override
        public Role save(Role role) {
            roles.add(role);
            return role;
        }

        @Override
        public Optional<Role> findById(RoleId id) {
            return roles.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Role> findByTenantIdAndName(TenantId tenantId, String name) {
            return roles.stream()
                .filter(r -> r.tenantId().equals(tenantId) && r.name().equals(name))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
            return findByTenantIdAndName(tenantId, name).isPresent();
        }
    };

    private final ProvisionDefaultRoles provisionDefaultRoles = new ProvisionDefaultRoles(roleRepository);

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

    private TenantDomainRepository domainRepository() {
        return new TenantDomainRepository() {
            @Override
            public Optional<Tenant> findByEmailDomain(String emailDomain) {
                if (emailDomain == null) {
                    return Optional.empty();
                }
                return bySlug.values().stream()
                    .filter(tenant -> emailDomain.equals(tenant.emailDomain()))
                    .findFirst();
            }

            @Override
            public boolean existsByEmailDomain(String emailDomain) {
                return findByEmailDomain(emailDomain).isPresent();
            }
        };
    }

    private RegisterTenant useCase() {
        return new RegisterTenant(repository(), domainRepository(), provisionDefaultRoles);
    }

    @Test
    void shouldRegisterTenantAsActive() {
        Tenant tenant = useCase().register(new RegisterTenantCommand("qsoft", "QSOFT", "qsoft.com"));

        assertThat(tenant).isNotNull();
        assertThat(tenant.id()).isNotNull();
        assertThat(tenant.slug()).isEqualTo("qsoft");
        assertThat(tenant.name()).isEqualTo("QSOFT");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void shouldProvisionDefaultRolesForNewTenant() {
        Tenant tenant = useCase().register(new RegisterTenantCommand("qsoft", "QSOFT", "qsoft.com"));

        assertThat(roles).hasSize(4);
        assertThat(roles).extracting(Role::name)
            .containsExactlyInAnyOrder("COLLABORATOR", "LEADER", "MANAGER", "EXECUTIVE");
        assertThat(roles).allMatch(r -> r.tenantId().equals(tenant.id()));
    }

    @Test
    void shouldRejectDuplicateSlug() {
        RegisterTenant useCase = useCase();
        useCase.register(new RegisterTenantCommand("qsoft", "QSOFT", "qsoft.com"));

        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand("qsoft", "Outra", "outra.com")))
            .isInstanceOf(TenantSlugAlreadyExistsException.class);
    }

    @Test
    void shouldRejectDuplicateEmailDomain() {
        RegisterTenant useCase = useCase();
        useCase.register(new RegisterTenantCommand("qsoft", "QSOFT", "qsoft.com"));

        assertThatThrownBy(() -> useCase.register(
            new RegisterTenantCommand("outra", "Outra", "qsoft.com")))
            .isInstanceOf(TenantDomainAlreadyExistsException.class);
    }

    @Test
    void shouldNormalizeEmailDomain() {
        Tenant tenant = useCase().register(
            new RegisterTenantCommand("qsoft", "QSOFT", "  @QSOFT.COM "));

        assertThat(tenant.emailDomain()).isEqualTo("qsoft.com");
    }

    @Test
    void shouldRejectBlankEmailDomain() {
        assertThatThrownBy(() -> useCase().register(
            new RegisterTenantCommand("qsoft", "QSOFT", "   ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankSlug() {
        assertThatThrownBy(() -> useCase().register(new RegisterTenantCommand("  ", "QSOFT", "qsoft.com")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
