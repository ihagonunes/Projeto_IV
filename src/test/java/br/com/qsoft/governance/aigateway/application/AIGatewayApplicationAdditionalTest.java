package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;

import br.com.qsoft.governance.aigateway.domain.provider.Provider;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AIGatewayApplicationAdditionalTest {

    @Test
    void testChangeModelStatus() {
        TenantId tenantId = TenantId.random();
        UUID providerId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        Model model = Model.restore(
            modelId, tenantId, providerId, "gpt-4",
            ModelStatus.DISABLED, new BigDecimal("0.03"), new BigDecimal("0.06"),
            Instant.now(), Instant.now()
        );

        Map<UUID, Model> models = new LinkedHashMap<>();
        models.put(modelId, model);

        ModelRepository modelRepo = new ModelRepository() {
            @Override
            public Model save(Model m) {
                models.put(m.id(), m);
                return m;
            }

            @Override
            public Optional<Model> findByTenantIdAndId(TenantId t, UUID id) {
                return Optional.ofNullable(models.get(id)).filter(m -> m.tenantId().equals(t));
            }

            @Override
            public List<Model> findByTenantId(TenantId t) {
                return new ArrayList<>(models.values());
            }

            @Override
            public List<Model> findByTenantIdAndIdIn(TenantId t, Collection<UUID> ids) {
                return models.values().stream().filter(m -> m.tenantId().equals(t) && ids.contains(m.id())).toList();
            }

            @Override
            public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
                return false;
            }
        };

        ChangeModelStatus service = new ChangeModelStatus(modelRepo);

        assertThatThrownBy(() -> service.change(null))
            .isInstanceOf(IllegalArgumentException.class);

        // Make Available
        ChangeModelStatusCommand command1 = new ChangeModelStatusCommand(tenantId, modelId, ModelStatus.AVAILABLE);
        Model result1 = service.change(command1);
        assertThat(result1.status()).isEqualTo(ModelStatus.AVAILABLE);

        // Disable
        ChangeModelStatusCommand command2 = new ChangeModelStatusCommand(tenantId, modelId, ModelStatus.DISABLED);
        Model result2 = service.change(command2);
        assertThat(result2.status()).isEqualTo(ModelStatus.DISABLED);

        // Model not found
        ChangeModelStatusCommand notFoundCmd = new ChangeModelStatusCommand(tenantId, UUID.randomUUID(), ModelStatus.AVAILABLE);
        assertThatThrownBy(() -> service.change(notFoundCmd))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void testListModelsAndListProviders() {
        TenantId tenantId = TenantId.random();
        ModelRepository modelRepo = new ModelRepository() {
            @Override
            public Model save(Model model) { return model; }
            @Override
            public Optional<Model> findByTenantIdAndId(TenantId t, UUID id) { return Optional.empty(); }
            @Override
            public List<Model> findByTenantId(TenantId t) { return List.of(); }
            @Override
            public List<Model> findByTenantIdAndIdIn(TenantId t, Collection<UUID> ids) { return List.of(); }
            @Override
            public boolean existsByTenantIdAndName(TenantId t, String name) { return false; }
        };

        ProviderRepository providerRepo = new ProviderRepository() {
            @Override
            public Provider save(Provider provider) { return provider; }
            @Override
            public Optional<Provider> findByTenantIdAndId(TenantId t, UUID id) { return Optional.empty(); }
            @Override
            public List<Provider> findByTenantId(TenantId t) { return List.of(); }
            @Override
            public boolean existsByTenantIdAndName(TenantId t, String name) { return false; }
        };


        ListModels listModels = new ListModels(modelRepo);
        assertThat(listModels.list(tenantId)).isEmpty();

        ListProviders listProviders = new ListProviders(providerRepo);
        assertThat(listProviders.list(tenantId)).isEmpty();
    }

    @Test
    void testResolvedModelRecord() {
        UUID modelId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        ResolvedModel resolved = new ResolvedModel(modelId, "GPT-4", providerId, "OpenAI");
        assertThat(resolved.modelId()).isEqualTo(modelId);
        assertThat(resolved.modelName()).isEqualTo("GPT-4");
        assertThat(resolved.providerId()).isEqualTo(providerId);
        assertThat(resolved.providerName()).isEqualTo("OpenAI");
    }
}
