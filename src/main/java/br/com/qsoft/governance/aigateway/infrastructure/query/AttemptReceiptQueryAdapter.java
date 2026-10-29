package br.com.qsoft.governance.aigateway.infrastructure.query;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.api.query.AttemptReceiptQuery;
import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adapter nativo, tenant-aware e por dono do recibo durável (ADR-42). */
@Component
public class AttemptReceiptQueryAdapter implements AttemptReceiptQuery {

    private static final int MAX_IDS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;

    public AttemptReceiptQueryAdapter(JdbcTemplate jdbcTemplate,
                                      ModelRepository modelRepository,
                                      ProviderRepository providerRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    @Override
    public Map<UUID, ReceiptInfo> receipts(TenantId tenantId, UserId owner,
                                           Set<UUID> requestIds) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(owner, "owner");
        if (requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }
        if (requestIds.size() > MAX_IDS) {
            throw new IllegalArgumentException("no máximo " + MAX_IDS + " requestIds");
        }

        List<UUID> ids = List.copyOf(requestIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> params = new ArrayList<>(ids.size() + 2);
        params.add(tenantId.value());
        params.add(owner.value());
        params.addAll(ids);

        List<AttemptRow> rows = jdbcTemplate.query("""
            SELECT request_id, model_id, provider_id, input_tokens, output_tokens,
                   estimated_cost, cost_status, latency_ms, execution_status
              FROM aigateway_request_attempts
             WHERE tenant_id = ?
               AND user_id = ?
               AND request_id IN (%s)
            """.formatted(placeholders), (rs, rowNum) -> new AttemptRow(
                rs.getObject("request_id", UUID.class),
                rs.getObject("model_id", UUID.class),
                rs.getObject("provider_id", UUID.class),
                rs.getObject("input_tokens", Long.class),
                rs.getObject("output_tokens", Long.class),
                rs.getObject("estimated_cost", BigDecimal.class),
                rs.getString("cost_status"),
                rs.getObject("latency_ms", Long.class),
                rs.getString("execution_status")), params.toArray());

        Map<UUID, Model> models = models(tenantId, rows);
        Map<UUID, Provider> providers = providers(tenantId);

        Map<UUID, ReceiptInfo> receipts = new LinkedHashMap<>();
        for (AttemptRow row : rows) {
            Model model = row.modelId() == null ? null : models.get(row.modelId());
            Provider provider = row.providerId() == null ? null : providers.get(row.providerId());
            receipts.put(row.requestId(), new ReceiptInfo(
                row.requestId(),
                row.modelId(),
                model == null ? null : model.name(),
                provider == null ? null : provider.name(),
                row.inputTokens(),
                row.outputTokens(),
                row.estimatedCost(),
                row.costStatus(),
                row.latencyMs(),
                row.executionStatus()));
        }
        return receipts;
    }

    private Map<UUID, Model> models(TenantId tenantId, List<AttemptRow> rows) {
        Set<UUID> ids = rows.stream()
            .map(AttemptRow::modelId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return modelRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
            .collect(Collectors.toMap(Model::id, Function.identity()));
    }

    private Map<UUID, Provider> providers(TenantId tenantId) {
        return providerRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(Provider::id, Function.identity()));
    }

    private record AttemptRow(UUID requestId, UUID modelId, UUID providerId, Long inputTokens,
                              Long outputTokens, BigDecimal estimatedCost, String costStatus,
                              Long latencyMs, String executionStatus) {
    }
}
