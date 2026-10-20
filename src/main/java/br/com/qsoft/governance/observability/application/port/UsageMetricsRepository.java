package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Porta de LEITURA (read model) das métricas operacionais do dashboard
 * (SPI-88/89), sobre {@code observability_usage_records}.
 * <p>
 * Toda consulta é <b>tenant-aware</b> (filtro explícito por {@code tenant_id}) e
 * recebe o {@link VisibilityScope} resolvido pela aplicação a partir das
 * concessões do usuário ({@code AuthorizationQuery.visibilityFor}, ADR-36) —
 * o frontend nunca decide o escopo.
 */
public interface UsageMetricsRepository {

    /** Série temporal de requisições por período (dia/semana/mês). */
    List<UsageSeriesPoint> requestsByPeriod(UsageMetricsFilter filter);

    /** Total de usuários distintos com uso no período/filtro. */
    long activeUsers(UsageMetricsFilter filter);

    /**
     * Tokens e custo agregados por departamento (contrato anterior mantido
     * para o status de orçamento — SPEC-02).
     */
    List<DepartmentCost> costByDepartment(UsageMetricsFilter filter);

    /** Tokens e custo agrupados por departamento, modelo ou usuário (SPI-97/98). */
    List<CostGroup> costGroups(UsageMetricsFilter filter, CostGroupBy groupBy);

    /** Granularidade da série temporal (SPI-88). */
    enum Granularity {
        DAY,
        WEEK,
        MONTH
    }

    /** Dimensão de agrupamento do custo (SPI-97/98/99). */
    enum CostGroupBy {
        DEPARTMENT,
        MODEL,
        USER
    }

    /**
     * Filtros já resolvidos: o escopo de visibilidade (nunca do cliente), o
     * departamento/modelo/usuário pedidos (já validados contra o escopo) e o
     * período.
     */
    record UsageMetricsFilter(
        TenantId tenantId,
        VisibilityScope visibility,
        UUID requestedDepartmentId,
        UUID requestedModelId,
        UUID requestedUserId,
        Instant from,
        Instant to,
        Granularity granularity
    ) {
        public UsageMetricsFilter {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
            Objects.requireNonNull(visibility, "visibility é obrigatória.");
        }

        /** Compatibilidade com os consumidores anteriores aos filtros de modelo/usuário. */
        public UsageMetricsFilter(TenantId tenantId, VisibilityScope visibility,
                                  UUID requestedDepartmentId, Instant from, Instant to,
                                  Granularity granularity) {
            this(tenantId, visibility, requestedDepartmentId, null, null, from, to, granularity);
        }
    }

    /** Ponto da série temporal. */
    record UsageSeriesPoint(String period, long requests) {
    }

    /** Consumo/financeiro por departamento. */
    record DepartmentCost(UUID departmentId, long requests, long inputTokens,
                          long outputTokens, java.math.BigDecimal estimatedCost) {
    }

    /**
     * Grupo de custo (SPI-113/114/115). {@code key} é o id da dimensão pedida;
     * pode ser nulo quando a linha operacional não tem departamento/modelo/usuário.
     * O rótulo NÃO é resolvido aqui: o observability não pode depender do
     * aigateway (allowlist) e o front resolve nomes pelo catálogo.
     */
    record CostGroup(UUID key, long requests, long inputTokens, long outputTokens,
                     java.math.BigDecimal estimatedCost) {
    }
}
