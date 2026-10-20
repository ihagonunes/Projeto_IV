package br.com.qsoft.governance.department.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Orçamento de um mês específico de um departamento (ADR-06/SPEC-02). A linha
 * do mês tem precedência sobre o {@code monthly_budget} padrão. Valores são
 * sempre em UTC/mês (YearMonth), sem hora.
 */
public final class MonthlyBudget {

    public static final int DEFAULT_ALERT_THRESHOLD_PCT = 80;
    private static final int THRESHOLD_MIN = 1;
    private static final int THRESHOLD_MAX = 100;

    private final UUID id;
    private final TenantId tenantId;
    private final UUID departmentId;
    private final YearMonth month;
    private BigDecimal amount;
    private int alertThresholdPct;
    private UUID updatedBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private MonthlyBudget(UUID id, TenantId tenantId, UUID departmentId, YearMonth month,
                          BigDecimal amount, int alertThresholdPct, UUID updatedBy,
                          Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
        this.month = Objects.requireNonNull(month, "month");
        this.amount = requireAmount(amount);
        this.alertThresholdPct = requireThreshold(alertThresholdPct);
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static MonthlyBudget create(UUID id, TenantId tenantId, UUID departmentId,
                                       YearMonth month, BigDecimal amount,
                                       int alertThresholdPct, UUID updatedBy) {
        Instant now = Instant.now();
        return new MonthlyBudget(id, tenantId, departmentId, month, amount, alertThresholdPct,
            updatedBy, now, now);
    }

    public static MonthlyBudget restore(UUID id, TenantId tenantId, UUID departmentId,
                                        YearMonth month, BigDecimal amount,
                                        int alertThresholdPct, UUID updatedBy,
                                        Instant createdAt, Instant updatedAt) {
        return new MonthlyBudget(id, tenantId, departmentId, month, amount, alertThresholdPct,
            updatedBy, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public YearMonth month() {
        return month;
    }

    public BigDecimal amount() {
        return amount;
    }

    public int alertThresholdPct() {
        return alertThresholdPct;
    }

    public UUID updatedBy() {
        return updatedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Upsert: altera valor, limiar e autor, mantendo o mês e a identidade. */
    public MonthlyBudget change(BigDecimal newAmount, int newThreshold, UUID actor) {
        this.amount = requireAmount(newAmount);
        this.alertThresholdPct = requireThreshold(newThreshold);
        this.updatedBy = Objects.requireNonNull(actor, "actor");
        this.updatedAt = Instant.now();
        return this;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount é obrigatório.");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount não pode ser negativo.");
        }
        return amount;
    }

    private static int requireThreshold(int threshold) {
        if (threshold < THRESHOLD_MIN || threshold > THRESHOLD_MAX) {
            throw new IllegalArgumentException(
                "alertThresholdPct deve estar entre " + THRESHOLD_MIN + " e " + THRESHOLD_MAX + ".");
        }
        return threshold;
    }
}
