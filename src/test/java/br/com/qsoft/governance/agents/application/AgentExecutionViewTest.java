package br.com.qsoft.governance.agents.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.agents.domain.ExecutionStepStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentExecutionViewTest {

    @Test
    void costWithoutRequestsIsNotApplicable() {
        var cost = AgentExecutionView.aggregateCost(List.of(
            step("t", null, null), step("r", null, null)));

        assertThat(cost.status()).isEqualTo("NOT_APPLICABLE");
        assertThat(cost.estimatedCost()).isNull();
    }

    @Test
    void knownCostsAreSummed() {
        var cost = AgentExecutionView.aggregateCost(List.of(
            step("a", UUID.randomUUID(), receipt("KNOWN", "0.10")),
            step("b", UUID.randomUUID(), receipt("KNOWN", "0.25"))));

        assertThat(cost.status()).isEqualTo("KNOWN");
        assertThat(cost.estimatedCost()).isEqualByComparingTo("0.35");
    }

    @Test
    void inconclusiveOrMissingReceiptNeverBecomesZero() {
        var inconclusive = AgentExecutionView.aggregateCost(List.of(
            step("a", UUID.randomUUID(), receipt("KNOWN", "0.10")),
            step("b", UUID.randomUUID(), receipt("INCONCLUSIVE", null))));
        assertThat(inconclusive.status()).isEqualTo("INCONCLUSIVE");
        assertThat(inconclusive.estimatedCost()).isNull();

        var missing = AgentExecutionView.aggregateCost(List.of(
            step("a", UUID.randomUUID(), null)));
        assertThat(missing.status()).isEqualTo("INCONCLUSIVE");
        assertThat(missing.estimatedCost()).isNull();
    }

    private static AgentExecutionView.StepView step(String nodeId, UUID requestId,
                                                    ReceiptInfo receipt) {
        return new AgentExecutionView.StepView(nodeId, "agent",
            ExecutionStepStatus.SUCCEEDED, requestId, null, null, null, receipt);
    }

    private static ReceiptInfo receipt(String status, String cost) {
        return new ReceiptInfo(UUID.randomUUID(), UUID.randomUUID(), "model", "provider",
            10L, 5L, cost == null ? null : new BigDecimal(cost), status, 100L, "SUCCEEDED");
    }
}
