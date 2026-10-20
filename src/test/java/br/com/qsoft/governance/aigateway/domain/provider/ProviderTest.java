package br.com.qsoft.governance.aigateway.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;


/**
 * Testes unitários do agregado {@link Provider}.
 * Domínio puro — sem Spring, sem banco, sem mocks.
 * Cobre os branches faltantes: line 66.7%, branch 50%.
 */
class ProviderTest {

    private final TenantId tenantId = TenantId.random();

    private Provider activeProvider() {
        return Provider.create(UUID.randomUUID(), tenantId, "OpenAI", ProviderKind.STUB,
            null, null, null);
    }

    // -------------------------------------------------------------------------
    // create — validação de nome
    // -------------------------------------------------------------------------

    @Test
    void deve_criarProviderAtivo_quando_nomeValido() {
        Provider p = Provider.create(UUID.randomUUID(), tenantId, "OpenAI", ProviderKind.STUB,
            null, null, null);

        assertThat(p.name()).isEqualTo("OpenAI");
        assertThat(p.status()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(p.isActive()).isTrue();
        assertThat(p.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void deve_lancarExcecao_quando_nomeVazio() {
        assertThatThrownBy(() -> Provider.create(UUID.randomUUID(), tenantId, "",
            ProviderKind.STUB, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_nomeApenasEspacos() {
        assertThatThrownBy(() -> Provider.create(UUID.randomUUID(), tenantId, "   ",
            ProviderKind.STUB, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_nomeNulo() {
        assertThatThrownBy(() -> Provider.create(UUID.randomUUID(), tenantId, null,
            ProviderKind.STUB, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deve_lancarExcecao_quando_nomeMaiorQue150Caracteres() {
        String nomeGigante = "A".repeat(151);
        assertThatThrownBy(() -> Provider.create(UUID.randomUUID(), tenantId, nomeGigante,
            ProviderKind.STUB, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("150");
    }

    @Test
    void deve_aceitarNomeComExatamente150Caracteres() {
        String nome = "B".repeat(150);
        Provider p = Provider.create(UUID.randomUUID(), tenantId, nome, ProviderKind.STUB,
            null, null, null);
        assertThat(p.name()).hasSize(150);
    }

    // -------------------------------------------------------------------------
    // rename
    // -------------------------------------------------------------------------

    @Test
    void deve_renomear_quando_novoNomeValido() {
        Provider p = activeProvider();

        p.rename("Anthropic");

        assertThat(p.name()).isEqualTo("Anthropic");
    }

    @Test
    void deve_lancarExcecao_quando_renameComNomeBlank() {
        Provider p = activeProvider();

        assertThatThrownBy(() -> p.rename("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // activate / deactivate — idempotência
    // -------------------------------------------------------------------------

    @Test
    void deve_desativar_quando_estaAtivo() {
        Provider p = activeProvider();

        p.deactivate();

        assertThat(p.status()).isEqualTo(ProviderStatus.INACTIVE);
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void deve_manterstatus_quando_deactivateJaInativo() {
        Provider p = activeProvider();
        p.deactivate();
        Instant before = p.updatedAt();

        p.deactivate(); // idempotente

        assertThat(p.status()).isEqualTo(ProviderStatus.INACTIVE);
        assertThat(p.updatedAt()).isEqualTo(before);
    }

    @Test
    void deve_reativar_quando_estaInativo() {
        Provider p = activeProvider();
        p.deactivate();

        p.activate();

        assertThat(p.status()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(p.isActive()).isTrue();
    }

    @Test
    void deve_manterStatus_quando_activateJaAtivo() {
        Provider p = activeProvider();
        Instant before = p.updatedAt();

        p.activate(); // idempotente

        assertThat(p.status()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(p.updatedAt()).isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // restore
    // -------------------------------------------------------------------------

    @Test
    void deve_restaurarComStatusInativo_quando_persistido() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Provider p = Provider.restore(id, tenantId, "Google", ProviderKind.STUB, null, null,
            30000, ProviderStatus.INACTIVE, now, now);

        assertThat(p.id()).isEqualTo(id);
        assertThat(p.status()).isEqualTo(ProviderStatus.INACTIVE);
        assertThat(p.isActive()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Acessores básicos
    // -------------------------------------------------------------------------

    @Test
    void deve_retornarCamposCorretos_quando_criado() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Provider p = Provider.restore(id, tenantId, "Azure", ProviderKind.STUB, null, null,
            30000, ProviderStatus.ACTIVE, now, now);

        assertThat(p.id()).isEqualTo(id);
        assertThat(p.tenantId()).isEqualTo(tenantId);
        assertThat(p.name()).isEqualTo("Azure");
        assertThat(p.createdAt()).isEqualTo(now);
        assertThat(p.updatedAt()).isEqualTo(now);
    }
}
