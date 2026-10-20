package br.com.qsoft.governance.aigateway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;


/**
 * Testes unitários do agregado {@link Model}.
 * Domínio puro — sem Spring, sem banco, sem mocks.
 * Cobre os branches faltantes: line 67.8%, branch 62.5%.
 */
class ModelTest {

    private final TenantId tenantId = TenantId.random();
    private final UUID providerId = UUID.randomUUID();

    private Model model(BigDecimal inputPrice, BigDecimal outputPrice) {
        return Model.create(UUID.randomUUID(), tenantId, providerId, "gpt-4", null,
            inputPrice, outputPrice);
    }

    private Model defaultModel() {
        return model(new BigDecimal("0.010000"), new BigDecimal("0.030000"));
    }

    // -------------------------------------------------------------------------
    // requireName — via create() e rename()
    // -------------------------------------------------------------------------

    @Test
    void deve_lancarExcecao_quando_nomeNuloAoCriar() {
        assertThatThrownBy(() ->
            Model.create(UUID.randomUUID(), tenantId, providerId, null, null,
                new BigDecimal("0.01"), new BigDecimal("0.03")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_renameComNomeVazio() {
        Model m = defaultModel();
        assertThatThrownBy(() -> m.rename(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_renameComNomeApenasEspacos() {
        Model m = defaultModel();
        assertThatThrownBy(() -> m.rename("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_nomeMaiorQue150Caracteres() {
        String nomeGigante = "X".repeat(151);
        assertThatThrownBy(() ->
            Model.create(UUID.randomUUID(), tenantId, providerId, nomeGigante, null,
                new BigDecimal("0.01"), new BigDecimal("0.03")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("150");
    }

    @Test
    void deve_aceitarNomeComExatamente150Caracteres() {
        String nome = "M".repeat(150);
        Model m = Model.create(UUID.randomUUID(), tenantId, providerId, nome, null, null, null);
        assertThat(m.name()).hasSize(150);
    }

    // -------------------------------------------------------------------------
    // requirePrice — preço negativo
    // -------------------------------------------------------------------------

    @Test
    void deve_lancarExcecao_quando_inputPriceNegativo() {
        assertThatThrownBy(() ->
            Model.create(UUID.randomUUID(), tenantId, providerId, "gpt-4", null,
                new BigDecimal("-0.01"), new BigDecimal("0.03")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativo");
    }

    @Test
    void deve_lancarExcecao_quando_outputPriceNegativo() {
        assertThatThrownBy(() ->
            Model.create(UUID.randomUUID(), tenantId, providerId, "gpt-4", null,
                new BigDecimal("0.01"), new BigDecimal("-0.03")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativo");
    }

    @Test
    void deve_aceitarPrecoNulo_quando_modelSemPreco() {
        Model m = Model.create(UUID.randomUUID(), tenantId, providerId, "local-llm", null, null, null);

        assertThat(m.inputPricePer1k()).isNull();
        assertThat(m.outputPricePer1k()).isNull();
    }

    // -------------------------------------------------------------------------
    // estimatedCost — branches de preço nulo
    // -------------------------------------------------------------------------

    @Test
    void deve_criarModelDisponivel_quando_nomeValido() {
        Model m = defaultModel();

        assertThat(m.name()).isEqualTo("gpt-4");
        assertThat(m.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(m.isAvailable()).isTrue();
        assertThat(m.tenantId()).isEqualTo(tenantId);
    }

    // -------------------------------------------------------------------------
    // estimatedCost — branches de preço nulo (via restore)
    // -------------------------------------------------------------------------

    @Test
    void deve_calcularCustoZero_quando_ambosPrecosSaoNulos() {
        // restore() não passa por requirePrice, portanto aceita null
        Model m = Model.restore(UUID.randomUUID(), tenantId, providerId, "local", null,
            ModelStatus.AVAILABLE, null, null, Instant.now(), Instant.now());

        assertThat(m.estimatedCost(1000, 2000)).isEqualByComparingTo("0.000000");
    }

    @Test
    void deve_calcularCusto_quando_apenasInputPrecoNulo() {
        // inputPrice = null, outputPrice = 0.03 → apenas output contribui
        Model m = Model.restore(UUID.randomUUID(), tenantId, providerId, "m", null,
            ModelStatus.AVAILABLE, null, new BigDecimal("0.030000"), Instant.now(), Instant.now());

        // (1000/1000 * 0) + (2000/1000 * 0.03) = 0.06
        assertThat(m.estimatedCost(1000, 2000)).isEqualByComparingTo("0.060000");
    }

    @Test
    void deve_calcularCusto_quando_apenasOutputPrecoNulo() {
        // inputPrice = 0.01, outputPrice = null → apenas input contribui
        Model m = Model.restore(UUID.randomUUID(), tenantId, providerId, "m", null,
            ModelStatus.AVAILABLE, new BigDecimal("0.010000"), null, Instant.now(), Instant.now());

        // (1000/1000 * 0.01) + (2000/1000 * 0) = 0.01
        assertThat(m.estimatedCost(1000, 2000)).isEqualByComparingTo("0.010000");
    }

    @Test
    void deve_lancarExcecao_quando_tokensNegativos() {
        Model m = defaultModel();

        assertThatThrownBy(() -> m.estimatedCost(-1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativos");

        assertThatThrownBy(() -> m.estimatedCost(0, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativos");
    }

    // -------------------------------------------------------------------------
    // makeAvailable / disable — idempotência
    // -------------------------------------------------------------------------

    @Test
    void deve_desabilitar_quando_estaDisponivel() {
        Model m = defaultModel();

        m.disable();

        assertThat(m.status()).isEqualTo(ModelStatus.DISABLED);
        assertThat(m.isAvailable()).isFalse();
    }

    @Test
    void deve_manterStatus_quando_disableJaEstaDesabilitado() {
        Model m = defaultModel();
        m.disable();
        Instant before = m.updatedAt();

        m.disable(); // idempotente

        assertThat(m.status()).isEqualTo(ModelStatus.DISABLED);
        assertThat(m.updatedAt()).isEqualTo(before);
    }

    @Test
    void deve_reativar_quando_estaDesabilitado() {
        Model m = defaultModel();
        m.disable();

        m.makeAvailable();

        assertThat(m.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(m.isAvailable()).isTrue();
    }

    @Test
    void deve_manterStatus_quando_makeAvailableJaDisponivel() {
        Model m = defaultModel();
        Instant before = m.updatedAt();

        m.makeAvailable(); // idempotente

        assertThat(m.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(m.updatedAt()).isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // rename e changePrices
    // -------------------------------------------------------------------------

    @Test
    void deve_renomear_quando_novoNomeValido() {
        Model m = defaultModel();

        m.rename("claude-3");

        assertThat(m.name()).isEqualTo("claude-3");
    }

    @Test
    void deve_alterarPrecos_quando_validoIncluindoNulos() {
        Model m = defaultModel();

        m.changePrices(null, null);

        assertThat(m.inputPricePer1k()).isNull();
        assertThat(m.outputPricePer1k()).isNull();
    }

    // -------------------------------------------------------------------------
    // restore — campos básicos
    // -------------------------------------------------------------------------

    @Test
    void deve_restaurarModelDesabilitado_quando_persistido() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Model m = Model.restore(id, tenantId, providerId, "gpt-4", null, ModelStatus.DISABLED,
            new BigDecimal("0.01"), new BigDecimal("0.03"), now, now);

        assertThat(m.id()).isEqualTo(id);
        assertThat(m.status()).isEqualTo(ModelStatus.DISABLED);
        assertThat(m.isAvailable()).isFalse();
        assertThat(m.createdAt()).isEqualTo(now);
    }
}
