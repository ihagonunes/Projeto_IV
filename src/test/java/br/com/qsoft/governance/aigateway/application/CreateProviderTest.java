package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;


/**
 * Testes unitários de {@link CreateProvider}.
 * Usa fake em memória — sem Spring, sem Mockito.
 */
class CreateProviderTest {

    private final TenantId tenantId = TenantId.random();

    // ── Fake em memória ────────────────────────────────────────────────────────

    private final Map<String, Provider> storedByName = new LinkedHashMap<>();
    private final List<Provider> saved = new ArrayList<>();

    private final ProviderRepository providerRepository = new ProviderRepository() {
        @Override
        public Provider save(Provider provider) {
            storedByName.put(provider.tenantId() + "|" + provider.name(), provider);
            saved.add(provider);
            return provider;
        }

        @Override
        public Optional<Provider> findByTenantIdAndId(TenantId tid, UUID id) {
            return storedByName.values().stream()
                .filter(p -> p.tenantId().equals(tid) && p.id().equals(id))
                .findFirst();
        }

        @Override
        public List<Provider> findByTenantId(TenantId tid) {
            return storedByName.values().stream()
                .filter(p -> p.tenantId().equals(tid))
                .toList();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tid, String name) {
            return storedByName.containsKey(tid + "|" + name);
        }
    };

    private final CreateProvider useCase = new CreateProvider(providerRepository);

    // ── Helper ────────────────────────────────────────────────────────────────

    private Provider existingProvider(String name) {
        Provider p = Provider.restore(UUID.randomUUID(), tenantId, name,
            ProviderStatus.ACTIVE, Instant.now(), Instant.now());
        storedByName.put(tenantId + "|" + name, p);
        return p;
    }

    // ── Testes ────────────────────────────────────────────────────────────────

    @Test
    void deve_criarProvider_quando_dadosValidos() {
        Provider result = useCase.create(new CreateProviderCommand(tenantId, "OpenAI"));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("OpenAI");
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.status()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(saved).hasSize(1);
    }

    @Test
    void deve_trimNome_quando_nomeComEspacos() {
        Provider result = useCase.create(new CreateProviderCommand(tenantId, "  Anthropic  "));

        assertThat(result.name()).isEqualTo("Anthropic");
    }

    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> useCase.create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(saved).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> useCase.create(new CreateProviderCommand(null, "OpenAI")))
            .isInstanceOf(NullPointerException.class);

        assertThat(saved).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeVazio() {
        assertThatThrownBy(() -> useCase.create(new CreateProviderCommand(tenantId, "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(saved).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeApenasEspacos() {
        assertThatThrownBy(() -> useCase.create(new CreateProviderCommand(tenantId, "   ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(saved).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeNulo() {
        assertThatThrownBy(() -> useCase.create(new CreateProviderCommand(tenantId, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        assertThat(saved).isEmpty();
    }

    @Test
    void deve_lancarExcecao_quando_nomeDuplicadoNoMesmoTenant() {
        existingProvider("OpenAI");

        assertThatThrownBy(() -> useCase.create(new CreateProviderCommand(tenantId, "OpenAI")))
            .isInstanceOf(ProviderNameAlreadyExistsException.class)
            .hasMessageContaining("OpenAI");

        // Confirma que nenhum segundo provider foi salvo
        assertThat(saved).isEmpty();
    }

    @Test
    void deve_permitirNomeDuplicado_quando_emTenantDiferente() {
        existingProvider("OpenAI"); // no tenantId original

        TenantId outroTenant = TenantId.random();
        Provider result = useCase.create(new CreateProviderCommand(outroTenant, "OpenAI"));

        // Isolamento multi-tenant: mesmo nome é OK em outro tenant
        assertThat(result.tenantId()).isEqualTo(outroTenant);
        assertThat(saved).hasSize(1);
    }
}
