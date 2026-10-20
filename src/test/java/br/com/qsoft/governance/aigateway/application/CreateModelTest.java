package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderStatus;
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


/**
 * Testes unitários de {@link CreateModel}.
 * Usa fakes em memória — sem Spring, sem Mockito.
 */
class CreateModelTest {

    private final TenantId tenantId = TenantId.random();
    private final UUID providerId = UUID.randomUUID();

    // ── Fakes em memória ──────────────────────────────────────────────────────

    private final Map<UUID, Provider> providers = new LinkedHashMap<>();
    private final Map<String, Model>  modelsByName = new LinkedHashMap<>();
    private final List<Model>         savedModels  = new ArrayList<>();

    private final ProviderRepository providerRepository = new ProviderRepository() {
        @Override
        public Provider save(Provider p) { providers.put(p.id(), p); return p; }

        @Override
        public Optional<Provider> findByTenantIdAndId(TenantId tid, UUID id) {
            return Optional.ofNullable(providers.get(id))
                .filter(p -> p.tenantId().equals(tid));
        }

        @Override
        public List<Provider> findByTenantId(TenantId tid) {
            return providers.values().stream().filter(p -> p.tenantId().equals(tid)).toList();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tid, String name) {
            return providers.values().stream()
                .anyMatch(p -> p.tenantId().equals(tid) && p.name().equals(name));
        }
    };

    private final ModelRepository modelRepository = new ModelRepository() {
        @Override
        public Model save(Model m) {
            modelsByName.put(m.tenantId() + "|" + m.name(), m);
            savedModels.add(m);
            return m;
        }

        @Override
        public Optional<Model> findByTenantIdAndId(TenantId tid, UUID id) {
            return modelsByName.values().stream()
                .filter(m -> m.tenantId().equals(tid) && m.id().equals(id))
                .findFirst();
        }

        @Override
        public List<Model> findByTenantId(TenantId tid) {
            return modelsByName.values().stream()
                .filter(m -> m.tenantId().equals(tid)).toList();
        }

        @Override
        public List<Model> findByTenantIdAndIdIn(TenantId tid, Collection<UUID> ids) {
            return modelsByName.values().stream()
                .filter(m -> m.tenantId().equals(tid) && ids.contains(m.id())).toList();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tid, String name) {
            return modelsByName.containsKey(tid + "|" + name);
        }
    };

    private final CreateModel useCase = new CreateModel(modelRepository, providerRepository);

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void activeProvider(UUID id) {
        providers.put(id, Provider.restore(id, tenantId, "OpenAI",
            br.com.qsoft.governance.aigateway.domain.provider.ProviderKind.STUB, null, null,
            30000, ProviderStatus.ACTIVE, Instant.now(), Instant.now()));
    }

    private void existingModel(String name) {
        Model m = Model.restore(UUID.randomUUID(), tenantId, providerId, name, null,
            ModelStatus.AVAILABLE, null, null, Instant.now(), Instant.now());
        modelsByName.put(tenantId + "|" + name, m);
    }

    private CreateModelCommand cmd(String name) {
        return new CreateModelCommand(tenantId, providerId, name, null,
            new BigDecimal("0.01"), new BigDecimal("0.03"));
    }

    // ── Testes ────────────────────────────────────────────────────────────────

    @Test
    void deve_criarModel_quando_dadosValidos() {
        activeProvider(providerId);

        Model result = useCase.create(cmd("gpt-4o"));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("gpt-4o");
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.providerId()).isEqualTo(providerId);
        assertThat(result.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(savedModels).hasSize(1);
    }

    @Test
    void deve_trimNome_quando_nomeComEspacos() {
        activeProvider(providerId);

        Model result = useCase.create(new CreateModelCommand(tenantId, providerId,
            "  claude-3  ", null, null, null));

        assertThat(result.name()).isEqualTo("claude-3");
    }

    @Test
    void deve_criarModel_quando_precosNulos() {
        activeProvider(providerId);

        Model result = useCase.create(new CreateModelCommand(tenantId, providerId,
            "llama3", null, null, null));

        assertThat(result.inputPricePer1k()).isNull();
        assertThat(result.outputPricePer1k()).isNull();
    }

    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> useCase.create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> useCase.create(
            new CreateModelCommand(null, providerId, "gpt-4o", null, null, null)))
            .isInstanceOf(NullPointerException.class);

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_providerIdNulo() {
        assertThatThrownBy(() -> useCase.create(
            new CreateModelCommand(tenantId, null, "gpt-4o", null, null, null)))
            .isInstanceOf(NullPointerException.class);

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeVazio() {
        assertThatThrownBy(() -> useCase.create(
            new CreateModelCommand(tenantId, providerId, "", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeApenasEspacos() {
        assertThatThrownBy(() -> useCase.create(
            new CreateModelCommand(tenantId, providerId, "   ", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeNulo() {
        assertThatThrownBy(() -> useCase.create(
            new CreateModelCommand(tenantId, providerId, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_providerNaoEncontrado() {
        // Nenhum provider registrado no repositório

        assertThatThrownBy(() -> useCase.create(cmd("gpt-4o")))
            .isInstanceOf(ProviderNotFoundException.class)
            .hasMessageContaining(providerId.toString());

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_providerDeOutroTenant() {
        // Provider existe mas pertence a outro tenant → isolamento multi-tenant
        UUID outroProviderId = UUID.randomUUID();
        providers.put(outroProviderId, Provider.restore(outroProviderId, TenantId.random(),
            "OpenAI", br.com.qsoft.governance.aigateway.domain.provider.ProviderKind.STUB,
            null, null, 30000, ProviderStatus.ACTIVE, Instant.now(), Instant.now()));

        assertThatThrownBy(() -> useCase.create(
            new CreateModelCommand(tenantId, outroProviderId, "gpt-4o", null, null, null)))
            .isInstanceOf(ProviderNotFoundException.class);

        assertThat(savedModels).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeDuplicadoNoMesmoTenant() {
        activeProvider(providerId);
        existingModel("gpt-4o");

        assertThatThrownBy(() -> useCase.create(cmd("gpt-4o")))
            .isInstanceOf(ModelNameAlreadyExistsException.class)
            .hasMessageContaining("gpt-4o");

        assertThat(savedModels).isEmpty();
    }
}
