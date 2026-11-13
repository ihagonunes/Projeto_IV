package br.com.qsoft.governance.aigateway.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.api.model.AllowedModel;
import br.com.qsoft.governance.aigateway.api.model.AllowedModels;
import br.com.qsoft.governance.aigateway.application.ResolveModelForUser;
import br.com.qsoft.governance.aigateway.application.ResolvedModel;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.model.ResolvedModelInfo;
import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllowedModelsQueryTest {

    private final ResolveModelForUser resolveModelForUser = mock(ResolveModelForUser.class);
    private final IdentityQuery identityQuery = mock(IdentityQuery.class);
    private final DepartmentModelPolicyQuery modelPolicyQuery =
        mock(DepartmentModelPolicyQuery.class);
    private final ModelRepository modelRepository = mock(ModelRepository.class);
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);

    private final ModelResolutionQueryAdapter adapter = new ModelResolutionQueryAdapter(
        resolveModelForUser, identityQuery, modelPolicyQuery, modelRepository,
        providerRepository);

    @Test
    void rejectsIdentityFromAnotherTenantBeforeAccessingPolicy() {
        TenantId tenant = TenantId.random();
        UserId user = UserId.random();
        when(identityQuery.getUserContext(user)).thenReturn(Optional.of(
            new UserContext(user, TenantId.random(), "COLLABORATOR", UUID.randomUUID(), "ACTIVE", "Ana")));

        assertThat(adapter.allowedModels(tenant, user)).isEqualTo(AllowedModels.empty());
        verifyNoInteractions(modelPolicyQuery, modelRepository, providerRepository);
    }

    @Test
    void returnsEmptyWithoutDepartmentPolicy() {
        TenantId tenant = TenantId.random();
        UserId user = UserId.random();
        UUID department = UUID.randomUUID();
        when(identityQuery.getUserContext(user)).thenReturn(Optional.of(
            new UserContext(user, tenant, "COLLABORATOR", department, "ACTIVE", "Ana")));

        assertThat(adapter.allowedModels(tenant, user)).isEqualTo(AllowedModels.empty());
        verifyNoInteractions(modelRepository, providerRepository);
    }

    @Test
    void mapsResolvedModelAndHidesRoutingFailure() {
        TenantId tenant = TenantId.random();
        UserId user = UserId.random();
        UUID model = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        ResolvedModel resolved = mock(ResolvedModel.class);
        when(resolved.modelId()).thenReturn(model);
        when(resolved.modelName()).thenReturn("Modelo");
        when(resolved.providerId()).thenReturn(provider);
        when(resolved.providerName()).thenReturn("Provider");
        when(resolveModelForUser.resolve(tenant, user, model)).thenReturn(resolved);
        when(resolveModelForUser.resolve(tenant, user, null)).thenThrow(new ModelUnavailableException());

        assertThat(adapter.resolve(tenant, user, model))
            .contains(new ResolvedModelInfo(model, "Modelo", provider, "Provider"));
        assertThat(adapter.resolve(tenant, user, null)).isEmpty();
    }

    @Test
    void returnsEmptyWhenUserHasNoDepartment() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        when(identityQuery.getUserContext(userId)).thenReturn(Optional.of(
            new UserContext(userId, tenantId, "COLLABORATOR", null, "ACTIVE", "Ana")));

        AllowedModels result = adapter.allowedModels(tenantId, userId);

        assertThat(result.defaultModelId()).isNull();
        assertThat(result.models()).isEmpty();
    }

    @Test
    void returnsPolicyModelsWithAvailabilityReasons() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID departmentId = UUID.randomUUID();
        UUID activeProviderId = UUID.randomUUID();
        UUID inactiveProviderId = UUID.randomUUID();

        Provider activeProvider = Provider.create(activeProviderId, tenantId, "Provider A",
            ProviderKind.STUB, null, null, null);
        Provider inactiveProvider = Provider.create(inactiveProviderId, tenantId, "Provider B",
            ProviderKind.STUB, null, null, null).deactivate();

        Model available = Model.create(UUID.randomUUID(), tenantId, activeProviderId, "Model A",
            null, BigDecimal.ONE, BigDecimal.ONE);
        Model disabled = Model.create(UUID.randomUUID(), tenantId, activeProviderId, "Model B",
            null, BigDecimal.ONE, BigDecimal.ONE).disable();
        Model providerInactive = Model.create(UUID.randomUUID(), tenantId, inactiveProviderId,
            "Model C", null, BigDecimal.ONE, BigDecimal.ONE);
        Model providerMissing = Model.create(UUID.randomUUID(), tenantId, UUID.randomUUID(),
            "Model D", null, BigDecimal.ONE, BigDecimal.ONE);

        Set<UUID> allowedIds = Set.of(available.id(), disabled.id(), providerInactive.id(), providerMissing.id());
        when(identityQuery.getUserContext(userId)).thenReturn(Optional.of(
            new UserContext(userId, tenantId, "COLLABORATOR", departmentId, "ACTIVE", "Ana")));
        when(modelPolicyQuery.findByDepartmentId(tenantId, departmentId)).thenReturn(
            Optional.of(new ModelPolicyInfo(departmentId, allowedIds, available.id())));
        when(modelRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(
            List.of(providerMissing, providerInactive, disabled, available));
        when(providerRepository.findByTenantId(tenantId)).thenReturn(
            List.of(activeProvider, inactiveProvider));

        AllowedModels result = adapter.allowedModels(tenantId, userId);

        assertThat(result.defaultModelId()).isEqualTo(available.id());
        assertThat(result.models()).extracting(AllowedModel::id)
            .containsExactly(available.id(), disabled.id(), providerInactive.id(), providerMissing.id());
        assertThat(result.models().getLast()).isEqualTo(
            new AllowedModel(providerMissing.id(), "Model D", null, false, "PROVIDER_INACTIVE"));
        assertThat(result.models())
            .filteredOn(model -> model.id().equals(available.id()))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.available()).isTrue();
                assertThat(model.unavailableReason()).isNull();
            });
        assertThat(result.models())
            .filteredOn(model -> model.id().equals(disabled.id()))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.available()).isFalse();
                assertThat(model.unavailableReason()).isEqualTo("MODEL_UNAVAILABLE");
            });
        assertThat(result.models())
            .filteredOn(model -> model.id().equals(providerInactive.id()))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.available()).isFalse();
                assertThat(model.unavailableReason()).isEqualTo("PROVIDER_INACTIVE");
            });
    }
}
