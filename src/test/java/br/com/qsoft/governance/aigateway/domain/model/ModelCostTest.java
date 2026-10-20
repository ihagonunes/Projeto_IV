package br.com.qsoft.governance.aigateway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cálculo do custo estimado (SPI-72 / ADR-13). */
class ModelCostTest {

    private Model model(BigDecimal inputPrice, BigDecimal outputPrice) {
        return Model.create(UUID.randomUUID(), TenantId.random(), UUID.randomUUID(), "gpt-4",
            null, inputPrice, outputPrice);
    }

    @Test
    void shouldComputeCostFromTokensAndPrices() {
        Model model = model(new BigDecimal("0.010000"), new BigDecimal("0.030000"));

        // (1000/1000 * 0.01) + (2000/1000 * 0.03) = 0.01 + 0.06 = 0.07
        assertThat(model.estimatedCost(1000, 2000)).isEqualByComparingTo("0.070000");
    }

    @Test
    void shouldComputeZeroWhenModelHasNoPrice() {
        assertThat(model(null, null).estimatedCost(500, 500)).isEqualByComparingTo("0.000000");
    }

    @Test
    void shouldHandleFractionalTokens() {
        Model model = model(new BigDecimal("0.010000"), new BigDecimal("0.030000"));

        // (100/1000 * 0.01) + (50/1000 * 0.03) = 0.001 + 0.0015 = 0.0025
        assertThat(model.estimatedCost(100, 50)).isEqualByComparingTo("0.002500");
    }
}
