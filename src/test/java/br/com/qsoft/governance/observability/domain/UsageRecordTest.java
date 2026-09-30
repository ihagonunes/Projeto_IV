package br.com.qsoft.governance.observability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários de {@link UsageRecord}.
 * Sem Spring, sem banco — domínio puro.
 * Cobertura de instrução: 67% → ≥ 70%.
 */
class UsageRecordTest {

    private static final UUID REQUEST_ID  = UUID.randomUUID();
    private static final UUID TENANT_UUID = UUID.randomUUID();
    private static final UUID DEPT_ID     = UUID.randomUUID();
    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID MODEL_ID    = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();

    private AIRequestCompleted defaultEvent() {
        return new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, DEPT_ID, USER_ID, MODEL_ID, PROVIDER_ID,
            1000L, 2000L, new BigDecimal("0.050000"), 350L, "SUCCESS"
        );
    }

    // -----------------------------------------------------------------------
    // fromEvent — factory principal (ADR-13)
    // -----------------------------------------------------------------------

    @Test
    void deve_criarRegistro_quando_eventoValido() {
        UUID recordId = UUID.randomUUID();
        AIRequestCompleted event = defaultEvent();

        UsageRecord record = UsageRecord.fromEvent(recordId, event);

        assertThat(record.id()).isEqualTo(recordId);
        assertThat(record.requestId()).isEqualTo(REQUEST_ID);
        assertThat(record.tenantId()).isEqualTo(TenantId.of(TENANT_UUID));
        assertThat(record.departmentId()).isEqualTo(DEPT_ID);
        assertThat(record.userId()).isEqualTo(USER_ID);
        assertThat(record.modelId()).isEqualTo(MODEL_ID);
        assertThat(record.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(record.inputTokens()).isEqualTo(1000L);
        assertThat(record.outputTokens()).isEqualTo(2000L);
        assertThat(record.estimatedCost()).isEqualByComparingTo("0.050000");
        assertThat(record.latencyMs()).isEqualTo(350L);
        assertThat(record.status()).isEqualTo("SUCCESS");
        assertThat(record.createdAt()).isNotNull();
    }

    /** departmentId, userId, modelId, providerId são opcionais → aceita null. */
    @Test
    void deve_criarRegistro_quando_camposOpcionaisNulos() {
        UUID recordId = UUID.randomUUID();
        AIRequestCompleted event = new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, "SUCCESS"
        );

        UsageRecord record = UsageRecord.fromEvent(recordId, event);

        assertThat(record.departmentId()).isNull();
        assertThat(record.userId()).isNull();
        assertThat(record.modelId()).isNull();
        assertThat(record.providerId()).isNull();
    }

    // -----------------------------------------------------------------------
    // fromEvent — validações do AIRequestCompleted (record compact constructor)
    // -----------------------------------------------------------------------

    @Test
    void deve_lancarExcecao_quando_requestIdNulo() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            null, TENANT_UUID, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, "SUCCESS"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("requestId");
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            REQUEST_ID, null, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, "SUCCESS"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void deve_lancarExcecao_quando_estimatedCostNulo() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            0L, 0L, null, 0L, "SUCCESS"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("estimatedCost");
    }

    @Test
    void deve_lancarExcecao_quando_statusNulo() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("status");
    }

    @Test
    void deve_lancarExcecao_quando_inputTokensNegativo() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            -1L, 0L, BigDecimal.ZERO, 0L, "SUCCESS"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativos");
    }

    @Test
    void deve_lancarExcecao_quando_outputTokensNegativo() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            0L, -1L, BigDecimal.ZERO, 0L, "SUCCESS"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativos");
    }

    @Test
    void deve_lancarExcecao_quando_latencyMsNegativa() {
        assertThatThrownBy(() -> new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, -1L, "SUCCESS"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativos");
    }

    // -----------------------------------------------------------------------
    // restore — reconstrói o registro a partir da persistência
    // -----------------------------------------------------------------------

    @Test
    void deve_restaurarRegistroCompleto_quando_persistido() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(60);
        TenantId tenantId = TenantId.of(TENANT_UUID);

        UsageRecord record = UsageRecord.restore(
            id, REQUEST_ID, tenantId, DEPT_ID, USER_ID, MODEL_ID, PROVIDER_ID,
            500L, 1000L, new BigDecimal("0.025000"), 200L, "SUCCESS", createdAt);

        assertThat(record.id()).isEqualTo(id);
        assertThat(record.requestId()).isEqualTo(REQUEST_ID);
        assertThat(record.tenantId()).isEqualTo(tenantId);
        assertThat(record.inputTokens()).isEqualTo(500L);
        assertThat(record.outputTokens()).isEqualTo(1000L);
        assertThat(record.estimatedCost()).isEqualByComparingTo("0.025000");
        assertThat(record.latencyMs()).isEqualTo(200L);
        assertThat(record.status()).isEqualTo("SUCCESS");
        assertThat(record.createdAt()).isEqualTo(createdAt);
    }

    /** restore com campos opcionais null — não lança exceção. */
    @Test
    void deve_restaurarRegistro_quando_camposOpcionaisNulos() {
        TenantId tenantId = TenantId.of(TENANT_UUID);

        UsageRecord record = UsageRecord.restore(
            UUID.randomUUID(), REQUEST_ID, tenantId, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, "ERROR", Instant.now());

        assertThat(record.departmentId()).isNull();
        assertThat(record.userId()).isNull();
        assertThat(record.modelId()).isNull();
        assertThat(record.providerId()).isNull();
        assertThat(record.status()).isEqualTo("ERROR");
    }

    /** id nulo em restore → NullPointerException (requireNonNull). */
    @Test
    void deve_lancarExcecao_quando_idNuloNoRestore() {
        assertThatThrownBy(() -> UsageRecord.restore(
            null, REQUEST_ID, TenantId.of(TENANT_UUID), null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, "SUCCESS", Instant.now()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");
    }

    /** Tokens zerados e custo zero — caso limite válido. */
    @Test
    void deve_criarRegistro_quando_tokensECustoZerados() {
        AIRequestCompleted event = new AIRequestCompleted(
            REQUEST_ID, TENANT_UUID, null, null, null, null,
            0L, 0L, BigDecimal.ZERO, 0L, "SUCCESS"
        );

        UsageRecord record = UsageRecord.fromEvent(UUID.randomUUID(), event);

        assertThat(record.inputTokens()).isZero();
        assertThat(record.outputTokens()).isZero();
        assertThat(record.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(record.latencyMs()).isZero();
    }
}
