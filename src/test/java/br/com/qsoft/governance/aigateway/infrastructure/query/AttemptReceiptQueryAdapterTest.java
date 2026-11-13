package br.com.qsoft.governance.aigateway.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AttemptReceiptQueryAdapterTest {

    private final TenantId tenant = TenantId.random();
    private final UserId owner = UserId.random();
    private final ReceiptJdbc jdbc = new ReceiptJdbc();
    private final ModelRepository models = mock(ModelRepository.class);
    private final ProviderRepository providers = mock(ProviderRepository.class);
    private final AttemptReceiptQueryAdapter adapter = new AttemptReceiptQueryAdapter(jdbc, models, providers);

    @Test
    void usesTenantAndOwnerAndResolvesNamesOnlyFromTheirTenantCatalog() {
        UUID requestId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        BigDecimal cost = new BigDecimal("0.125");
        jdbc.rows.add(Map.of("request_id", requestId, "model_id", modelId, "provider_id", providerId,
            "input_tokens", 4L, "output_tokens", 5L, "estimated_cost", cost, "cost_status", "KNOWN",
            "latency_ms", 18L, "execution_status", "SUCCEEDED"));
        jdbc.rows.add(Map.of("request_id", missingId, "model_id", missingId, "provider_id", missingId,
            "cost_status", "INCONCLUSIVE", "execution_status", "FAILED"));
        when(models.findByTenantIdAndIdIn(tenant, Set.of(modelId, missingId))).thenReturn(List.of(
            Model.create(modelId, tenant, providerId, "Modelo", null, BigDecimal.ONE, BigDecimal.ONE)));
        when(providers.findByTenantId(tenant)).thenReturn(List.of(
            Provider.create(providerId, tenant, "Provider", ProviderKind.STUB, null, null, null)));

        assertThat(adapter.receipts(tenant, owner, Set.of(requestId, missingId)))
            .containsOnly(
                Map.entry(requestId, new ReceiptInfo(requestId, modelId, "Modelo", "Provider", 4L, 5L,
                    cost, "KNOWN", 18L, "SUCCEEDED")),
                Map.entry(missingId, new ReceiptInfo(missingId, missingId, null, null, null, null,
                    null, "INCONCLUSIVE", null, "FAILED")));
        assertThat(jdbc.sql).contains("tenant_id = ?", "user_id = ?", "request_id IN (?,?)");
        assertThat(jdbc.arguments.subList(0, 2)).containsExactly(tenant.value(), owner.value());
        assertThat(jdbc.arguments.subList(2, 4)).containsExactlyInAnyOrder(requestId, missingId);
    }

    @Test
    void keepsReceiptForAttemptWithoutModelOrProvider() {
        UUID requestId = UUID.randomUUID();
        jdbc.rows.add(Map.of("request_id", requestId, "execution_status", "FAILED"));

        assertThat(adapter.receipts(tenant, owner, Set.of(requestId)))
            .containsEntry(requestId, new ReceiptInfo(requestId, null, null, null, null, null, null, null, null, "FAILED"));
        verifyNoInteractions(models);
    }

    @Test
    void validatesBatchSizeBeforeAccessingDatabase() {
        assertThat(adapter.receipts(tenant, owner, null)).isEmpty();
        assertThat(adapter.receipts(tenant, owner, Set.of())).isEmpty();
        Set<UUID> oversized = IntStream.range(0, 101).mapToObj(index -> new UUID(0, index))
            .collect(Collectors.toSet());
        assertThatThrownBy(() -> adapter.receipts(tenant, owner, oversized))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100");
        assertThat(jdbc.sql).isNull();
        verifyNoInteractions(models, providers);
    }

    private static final class ReceiptJdbc extends JdbcTemplate {
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private String sql;
        private List<Object> arguments;

        @Override
        public <T> List<T> query(String statement, RowMapper<T> mapper, Object... parameters) {
            sql = statement;
            arguments = List.of(parameters);
            List<T> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                ResultSet rs = mock(ResultSet.class, invocation -> row.get(invocation.getArgument(0)));
                try {
                    result.add(mapper.mapRow(rs, result.size()));
                } catch (SQLException ex) {
                    throw new AssertionError(ex);
                }
            }
            return result;
        }
    }
}
