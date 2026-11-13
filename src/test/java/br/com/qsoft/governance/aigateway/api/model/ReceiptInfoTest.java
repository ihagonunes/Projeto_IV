package br.com.qsoft.governance.aigateway.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceiptInfoTest {

    @Test
    void preservesUnavailableMetricsForIncompleteAttempts() {
        UUID requestId = UUID.randomUUID();

        ReceiptInfo receipt = new ReceiptInfo(requestId, null, null, null, null, null, null,
            "INCONCLUSIVE", null, "FAILED");

        assertThat(receipt.requestId()).isEqualTo(requestId);
        assertThat(receipt.executionStatus()).isEqualTo("FAILED");
        assertThat(receipt.costStatus()).isEqualTo("INCONCLUSIVE");
        assertThat(receipt.inputTokens()).isNull();
        assertThat(receipt.outputTokens()).isNull();
        assertThat(receipt.estimatedCost()).isNull();
        assertThat(receipt.latencyMs()).isNull();
    }
}
