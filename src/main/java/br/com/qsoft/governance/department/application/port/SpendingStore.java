package br.com.qsoft.governance.department.application.port;

import br.com.qsoft.governance.department.domain.SpendingPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpendingStore {
    record Settings(String timezone, int alertPct) {}
    record Usage(BigDecimal spent, BigDecimal reserved) {}
    record Reservation(UUID requestId, UUID userId, YearMonth month, BigDecimal reserved,
                       BigDecimal actual, BigDecimal exchangeRate, String currency,
                       String reference, String state, Instant createdAt, UUID departmentId) {
        public Reservation(UUID requestId, UUID userId, YearMonth month, BigDecimal reserved,
                           BigDecimal actual, BigDecimal exchangeRate, String currency,
                           String reference, String state, Instant createdAt) {
            this(requestId, userId, month, reserved, actual, exchangeRate, currency, reference, state, createdAt, null);
        }
    }
    Settings lock(UUID tenant);
    void settings(UUID tenant, Settings settings);
    Optional<SpendingPlan> plan(UUID tenant, YearMonth month);
    Optional<SpendingPlan> previous(UUID tenant, YearMonth month);
    void save(UUID tenant, YearMonth month, SpendingPlan plan);
    Usage usage(UUID tenant, YearMonth month, UUID user);
    Usage companyUsage(UUID tenant, YearMonth month);
    Usage departmentUsage(UUID tenant, YearMonth month, UUID department);
    Optional<Reservation> reservation(UUID tenant, UUID request);
    void reserve(UUID tenant, Reservation reservation);
    void settle(UUID tenant, UUID request, BigDecimal actual);
    List<Reservation> pending(UUID tenant);
    void audit(UUID tenant, UUID actor, String operation, Object before, Object after);
}
