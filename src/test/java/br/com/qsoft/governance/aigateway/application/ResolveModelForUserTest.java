package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.domain.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Testes do roteamento determinístico (SPI-83/85/86). */
class ResolveModelForUserTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();
    private final UUID departmentId = UUID.randomUUID();

    private final Map<UUID, Model> models = new LinkedHashMap<>();
    private final Map<UUID, Provider> providers = new LinkedHashMap<>();
    private ModelPolicyInfo policy;
    private UUID userDepartmentId = departmentId;

    private final IdentityQuery identityQuery = id -> Optional.of(new UserContext(
        id, tenantId, "MANAGER", userDepartmentId, "ACTIVE"));

    private final DepartmentModelPolicyQuery modelPolicyQuery =
        (tenant, department) -> Optional.ofNullable(policy);

    private final ModelRepository modelRepository = new ModelRepository() {
        @Override
        public Model save(Model model) {
            models.put(model.id(), model);
            return model;
        }

        @Override
        public Optional<Model> findByTenantIdAndId(TenantId tenant, UUID modelId) {
            return Optional.ofNullable(models.get(modelId))
                .filter(model -> model.tenantId().equals(tenant));
        }

        @Override
        public List<Model> findByTenantId(TenantId tenant) {
            return models.values().stream().filter(m -> m.tenantId().equals(tenant)).toList();
        }

        @Override
        public List<Model> findByTenantIdAndIdIn(TenantId tenant, Collection<UUID> ids) {
            return models.values().stream()
                .filter(m -> m.tenantId().equals(tenant) && ids.contains(m.id()))
                .toList();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenant, String name) {
            return false;
        }
    };

    private final ProviderRepository providerRepository = new ProviderRepository() {
        @Override
        public Provider save(Provider provider) {
            providers.put(provider.id(), provider);
            return provider;
        }

        @Override
        public Optional<Provider> findByTenantIdAndId(TenantId tenant, UUID providerId) {
            return Optional.ofNullable(providers.get(providerId))
                .filter(provider -> provider.tenantId().equals(tenant));
        }

        @Override
        public List<Provider> findByTenantId(TenantId tenant) {
            return providers.values().stream().filter(p -> p.tenantId().equals(tenant)).toList();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenant, String name) {
            return false;
        }
    };

    private final ResolveModelForUser useCase = new ResolveModelForUser(
        identityQuery, modelPolicyQuery, modelRepository, providerRepository);

    private Provider provider(boolean active) {
        Provider provider = Provider.create(UUID.randomUUID(), tenantId, "OpenAI");
        if (!active) {
            provider.deactivate();
        }
        providers.put(provider.id(), provider);
        return provider;
    }

    private Model model(Provider provider, boolean available) {
        Model model = Model.create(UUID.randomUUID(), tenantId, provider.id(), "gpt-4", null, null);
        if (!available) {
            model.disable();
        }
        models.put(model.id(), model);
        return model;
    }

    @Test
    void shouldResolveRequestedModelWhenAllowed() {
        Provider provider = provider(true);
        Model model = model(provider, true);
        policy = new ModelPolicyInfo(departmentId, Set.of(model.id()), model.id());

        ResolvedModel resolved = useCase.resolve(tenantId, userId, model.id());

        assertThat(resolved.modelId()).isEqualTo(model.id());
        assertThat(resolved.providerId()).isEqualTo(provider.id());
    }

    @Test
    void shouldResolveDefaultWhenRequestedAbsent() {
        Provider provider = provider(true);
        Model model = model(provider, true);
        policy = new ModelPolicyInfo(departmentId, Set.of(model.id()), model.id());

        assertThat(useCase.resolve(tenantId, userId, null).modelId()).isEqualTo(model.id());
    }

    @Test
    void shouldRejectRequestedModelNotAllowed() {
        Provider provider = provider(true);
        Model allowed = model(provider, true);
        Model other = model(provider, true);
        policy = new ModelPolicyInfo(departmentId, Set.of(allowed.id()), allowed.id());

        assertThatThrownBy(() -> useCase.resolve(tenantId, userId, other.id()))
            .isInstanceOf(ModelNotAllowedException.class);
    }

    @Test
    void shouldRejectWhenNoPolicyConfigured() {
        policy = null;

        assertThatThrownBy(() -> useCase.resolve(tenantId, userId, null))
            .isInstanceOf(NoModelConfiguredException.class);
    }

    @Test
    void shouldRejectWhenUserHasNoDepartment() {
        userDepartmentId = null;
        policy = null;

        assertThatThrownBy(() -> useCase.resolve(tenantId, userId, null))
            .isInstanceOf(NoModelConfiguredException.class);
    }

    @Test
    void shouldRejectWhenModelDisabled() {
        Provider provider = provider(true);
        Model model = model(provider, false);
        policy = new ModelPolicyInfo(departmentId, Set.of(model.id()), model.id());

        assertThatThrownBy(() -> useCase.resolve(tenantId, userId, null))
            .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void shouldRejectWhenProviderInactive() {
        Provider provider = provider(false);
        Model model = model(provider, true);
        policy = new ModelPolicyInfo(departmentId, Set.of(model.id()), model.id());

        assertThatThrownBy(() -> useCase.resolve(tenantId, userId, null))
            .isInstanceOf(ProviderInactiveException.class);
    }
}
