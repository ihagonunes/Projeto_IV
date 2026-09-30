package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderStatus;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
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
import org.springframework.context.ApplicationEventPublisher;

/**
 * Testes unitários de {@link CompleteAIRequest}.
 * Usa fakes em memória + publisher capturador — sem Spring, sem Mockito.
 */
class CompleteAIRequestTest {

    private final TenantId tenantId   = TenantId.random();
    private final UUID     providerId = UUID.randomUUID();
    private final UUID     modelId    = UUID.randomUUID();

    // ── Fake: captura eventos publicados ─────────────────────────────────────

    private final List<Object> publishedEvents = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = publishedEvents::add;

    // ── Fakes em memória ──────────────────────────────────────────────────────

    private final Map<UUID, Model>    models    = new LinkedHashMap<>();
    private final Map<UUID, Provider> providers = new LinkedHashMap<>();

    private final ModelRepository modelRepository = new ModelRepository() {
        @Override
        public Model save(Model m) { models.put(m.id(), m); return m; }
        @Override
        public Optional<Model> findByTenantIdAndId(TenantId tid, UUID id) {
            return Optional.ofNullable(models.get(id))
                .filter(m -> m.tenantId().equals(tid));
        }
        @Override
        public List<Model> findByTenantId(TenantId tid) { return List.of(); }
        @Override
        public List<Model> findByTenantIdAndIdIn(TenantId tid, Collection<UUID> ids) {
            return models.values().stream()
                .filter(m -> m.tenantId().equals(tid) && ids.contains(m.id())).toList();
        }
        @Override
        public boolean existsByTenantIdAndName(TenantId tid, String name) { return false; }
    };

    private final ProviderRepository providerRepository = new ProviderRepository() {
        @Override
        public Provider save(Provider p) { providers.put(p.id(), p); return p; }
        @Override
        public Optional<Provider> findByTenantIdAndId(TenantId tid, UUID id) {
            return Optional.ofNullable(providers.get(id))
                .filter(p -> p.tenantId().equals(tid));
        }
        @Override
        public List<Provider> findByTenantId(TenantId tid) { return List.of(); }
        @Override
        public boolean existsByTenantIdAndName(TenantId tid, String name) { return false; }
    };

    private final CompleteAIRequest useCase =
        new CompleteAIRequest(modelRepository, providerRepository, eventPublisher);

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void availableModel(UUID id, UUID provId) {
        models.put(id, Model.restore(id, tenantId, provId, "gpt-4o",
            ModelStatus.AVAILABLE, new BigDecimal("0.01"), new BigDecimal("0.03"),
            Instant.now(), Instant.now()));
    }

    private void disabledModel(UUID id, UUID provId) {
        models.put(id, Model.restore(id, tenantId, provId, "gpt-4o-disabled",
            ModelStatus.DISABLED, null, null, Instant.now(), Instant.now()));
    }

    private void activeProvider(UUID id) {
        providers.put(id, Provider.restore(id, tenantId, "OpenAI",
            ProviderStatus.ACTIVE, Instant.now(), Instant.now()));
    }

    private void inactiveProvider(UUID id) {
        providers.put(id, Provider.restore(id, tenantId, "ClosedAI",
            ProviderStatus.INACTIVE, Instant.now(), Instant.now()));
    }

    private CompleteAIRequestCommand cmd(UUID reqId, String status) {
        return new CompleteAIRequestCommand(reqId, tenantId, null, null,
            modelId, 1000L, 2000L, 350L, status);
    }

    // ── Testes ────────────────────────────────────────────────────────────────

    @Test
    void deve_completarRequisicao_quando_dadosValidos() {
        availableModel(modelId, providerId);
        activeProvider(providerId);

        AIRequestCompleted event = useCase.complete(cmd(UUID.randomUUID(), "SUCCESS"));

        assertThat(event.modelId()).isEqualTo(modelId);
        assertThat(event.providerId()).isEqualTo(providerId);
        assertThat(event.tenantId()).isEqualTo(tenantId.value());
        assertThat(event.inputTokens()).isEqualTo(1000L);
        assertThat(event.outputTokens()).isEqualTo(2000L);
        assertThat(event.status()).isEqualTo("SUCCESS");
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(AIRequestCompleted.class);
    }

    @Test
    void deve_gerarRequestId_quando_requestIdNulo() {
        availableModel(modelId, providerId);
        activeProvider(providerId);

        AIRequestCompleted event = useCase.complete(cmd(null, "SUCCESS"));

        // requestId gerado aleatoriamente — deve ser não-nulo
        assertThat(event.requestId()).isNotNull();
    }

    @Test
    void deve_usarRequestIdFornecido_quando_naoNulo() {
        availableModel(modelId, providerId);
        activeProvider(providerId);

        UUID fixedId = UUID.randomUUID();
        AIRequestCompleted event = useCase.complete(cmd(fixedId, "SUCCESS"));

        assertThat(event.requestId()).isEqualTo(fixedId);
    }

    @Test
    void deve_usarStatusCompleted_quando_statusNulo() {
        availableModel(modelId, providerId);
        activeProvider(providerId);

        AIRequestCompleted event = useCase.complete(cmd(null, null));

        assertThat(event.status()).isEqualTo("COMPLETED");
    }

    @Test
    void deve_calcularCustoEstimado_quando_precosDefinidos() {
        availableModel(modelId, providerId);
        activeProvider(providerId);

        AIRequestCompleted event = useCase.complete(cmd(null, "SUCCESS"));

        // (1000/1000 * 0.01) + (2000/1000 * 0.03) = 0.01 + 0.06 = 0.07
        assertThat(event.estimatedCost()).isEqualByComparingTo("0.07");
    }

    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> useCase.complete(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> useCase.complete(
            new CompleteAIRequestCommand(null, null, null, null,
                modelId, 0L, 0L, 0L, "SUCCESS")))
            .isInstanceOf(NullPointerException.class);

        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_modelNaoEncontrado() {
        // Nenhum model no repositório

        assertThatThrownBy(() -> useCase.complete(cmd(null, "SUCCESS")))
            .isInstanceOf(ModelNotFoundException.class);

        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_modelDesabilitado() {
        disabledModel(modelId, providerId);

        assertThatThrownBy(() -> useCase.complete(cmd(null, "SUCCESS")))
            .isInstanceOf(ModelUnavailableException.class);

        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_providerNaoEncontrado() {
        availableModel(modelId, providerId);
        // Provider não registrado

        assertThatThrownBy(() -> useCase.complete(cmd(null, "SUCCESS")))
            .isInstanceOf(ProviderNotFoundException.class);

        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_providerInativo() {
        availableModel(modelId, providerId);
        inactiveProvider(providerId);

        assertThatThrownBy(() -> useCase.complete(cmd(null, "SUCCESS")))
            .isInstanceOf(ProviderInactiveException.class);

        assertThat(publishedEvents).isEmpty();
    }
}
