package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Porta de LEITURA (read model) das métricas operacionais do dashboard
 * (SPI-88/89), sobre {@code observability_usage_records}.
 * <p>
 * Toda consulta é <b>tenant-aware</b> (filtro explícito por {@code tenant_id}) e
 * recebe os filtros de <b>escopo</b> já resolvidos pela aplicação a partir das
 * concessões do usuário ({@code AuthorizationQuery.visibilityFor}, ADR-36) — o
 * frontend nunca decide o escopo.
 */
public interface UsageMetricsRepository {

    /** Série temporal de requisições por período (dia/semana/mês). */
    List<UsageSeriesPoint> requestsByPeriod(UsageMetricsFilter filter);

    /** Total de usuários distintos com uso no período/filtro. */
    long activeUsers(UsageMetricsFilter filter);

    /** Tokens e custo agregados por departamento. */
    List<DepartmentCost> costByDepartment(UsageMetricsFilter filter);

    /** Granularidade da série temporal (SPI-88). */
    enum Granularity {
        DAY,
        WEEK,
        MONTH
    }

    /**
     * Filtros já resolvidos (escopo aplicado).
     * {@code userId} restringe a um usuário (escopo SELF);
     * {@code departmentIds} restringe à união de departamentos (escopo DEPARTMENT);
     * vazio = todo o tenant (escopo TENANT, com filtro opcional pedido pelo cliente).
     */
    record UsageMetricsFilter(
        TenantId tenantId,
        UUID userId,
        Set<UUID> departmentIds,
        Instant from,
        Instant to,
        Granularity granularity
    ) {
        public UsageMetricsFilter {
            departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        }
    }

    /** Ponto da série temporal. */
    record UsageSeriesPoint(String period, long requests) {
    }

    /** Consumo/financeiro por departamento. */
    record DepartmentCost(UUID departmentId, long requests, long inputTokens,
                          long outputTokens, java.math.BigDecimal estimatedCost) {
    }
}
