package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Read model histórico separado da operação (ADR-26). IDs da fonte são textuais. */
public interface HistoricalDashboardQuery {
    Coverage coverage(TenantId tenantId);
    List<Option> departments(TenantId tenantId);
    List<Option> models(TenantId tenantId);
    Coverage coverage(Filter filter);
    List<Option> departments(Filter filter);
    List<Option> models(Filter filter);
    List<Daily> series(Filter filter);
    List<ModelCost> costs(Filter filter);
    long activeUsers(Filter filter);

    record Coverage(LocalDate from, LocalDate to) { }
    record Option(String id, String name) { }
    record Scope(boolean tenantWide, Set<String> departmentIds, String employeeId) {
        public Scope { departmentIds = Set.copyOf(departmentIds); }
        public static Scope tenant() { return new Scope(true, Set.of(), null); }
    }
    record Filter(TenantId tenantId, LocalDate from, LocalDate to,
                  String departmentId, String modelId, Scope scope, String requestedEmployeeId) {
        public Filter(TenantId tenantId, LocalDate from, LocalDate to, String departmentId, String modelId, Scope scope) {
            this(tenantId, from, to, departmentId, modelId, scope, null);
        }
        public Filter(TenantId tenantId, LocalDate from, LocalDate to, String departmentId, String modelId) {
            this(tenantId, from, to, departmentId, modelId, Scope.tenant(), null);
        }
    }
    record Daily(String period, long requests, long inputTokens, long outputTokens,
                 BigDecimal estimatedCostUsd, long unpricedRecords) {
        public Daily(String period, long requests) { this(period, requests, 0, 0, null, 0); }
    }
    record ModelCost(String modelId, String name, long requests, long inputTokens,
                     long outputTokens, BigDecimal estimatedCostUsd, long unpricedRecords) { }
    record Dashboard(String sourceKind, String currency, LocalDate from, LocalDate to,
                     Coverage coverage, long activeUsers, List<Option> departments,
                     List<Option> models, List<Daily> series, List<ModelCost> costs) { }
}
