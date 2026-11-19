package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.application.port.SpendingStore;
import br.com.qsoft.governance.department.domain.SpendingPlan;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SpendingJdbcStore implements SpendingStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public SpendingJdbcStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }
    @Override public Settings lock(UUID tenant) {
        jdbc.update("INSERT INTO department_spending_settings(tenant_id) VALUES (?) ON CONFLICT DO NOTHING", tenant);
        return jdbc.queryForObject("SELECT timezone,alert_pct FROM department_spending_settings WHERE tenant_id=? FOR UPDATE",
            (r, n) -> new Settings(r.getString(1), r.getInt(2)), tenant);
    }
    @Override public void settings(UUID tenant, Settings settings) {
        jdbc.update("UPDATE department_spending_settings SET timezone=?,alert_pct=? WHERE tenant_id=?",
            settings.timezone(), settings.alertPct(), tenant);
    }
    @Override public Optional<SpendingPlan> plan(UUID tenant, YearMonth month) {
        return jdbc.query("SELECT plan::text FROM department_spending_plans WHERE tenant_id=? AND month=?",
            (r, n) -> mapper.readValue(r.getString(1), SpendingPlan.class), tenant, month.toString()).stream().findFirst();
    }
    @Override public Optional<SpendingPlan> previous(UUID tenant, YearMonth month) {
        return jdbc.query("SELECT plan::text FROM department_spending_plans WHERE tenant_id=? AND month<? ORDER BY month DESC LIMIT 1",
            (r, n) -> mapper.readValue(r.getString(1), SpendingPlan.class), tenant, month.toString()).stream().findFirst();
    }
    @Override public void save(UUID tenant, YearMonth month, SpendingPlan plan) {
        jdbc.update("INSERT INTO department_spending_plans(tenant_id,month,plan) VALUES (?,?,?::jsonb) "
            + "ON CONFLICT(tenant_id,month) DO UPDATE SET plan=excluded.plan", tenant, month.toString(), mapper.writeValueAsString(plan));
    }
    @Override public Usage usage(UUID tenant, YearMonth month, UUID user) {
        return jdbc.queryForObject("SELECT coalesce(sum(actual) FILTER(WHERE state='SETTLED'),0), "
            + "coalesce(sum(reserved) FILTER(WHERE state='PENDING'),0) FROM department_spending_reservations "
            + "WHERE tenant_id=? AND month=? AND user_id=?",
            (r, n) -> new Usage(r.getBigDecimal(1), r.getBigDecimal(2)), tenant, month.toString(), user);
    }
    private Reservation row(ResultSet r, int ignored) throws SQLException {
        return new Reservation(r.getObject("request_id", UUID.class), r.getObject("user_id", UUID.class),
            YearMonth.parse(r.getString("month")), r.getBigDecimal("reserved"), r.getBigDecimal("actual"),
            r.getBigDecimal("exchange_rate"), r.getString("currency"), r.getString("rate_reference"),
            r.getString("state"), r.getTimestamp("created_at").toInstant(), r.getObject("department_id", UUID.class));
    }
    @Override public Optional<Reservation> reservation(UUID tenant, UUID request) {
        return jdbc.query("SELECT * FROM department_spending_reservations WHERE tenant_id=? AND request_id=?",
            this::row, tenant, request).stream().findFirst();
    }
    @Override public void reserve(UUID tenant, Reservation r) {
        jdbc.update("INSERT INTO department_spending_reservations(tenant_id,request_id,user_id,month,reserved,"
            + "exchange_rate,currency,rate_reference,department_id,state) VALUES (?,?,?,?,?,?,?,?,?,'PENDING')",
            tenant, r.requestId(), r.userId(), r.month().toString(), r.reserved(), r.exchangeRate(), r.currency(), r.reference(), r.departmentId());
    }
    @Override public Usage companyUsage(UUID tenant, YearMonth month) {
        return jdbc.queryForObject("SELECT coalesce(sum(actual) FILTER(WHERE state='SETTLED'),0), "
            + "coalesce(sum(reserved) FILTER(WHERE state='PENDING'),0) FROM department_spending_reservations WHERE tenant_id=? AND month=?",
            (r, n) -> new Usage(r.getBigDecimal(1), r.getBigDecimal(2)), tenant, month.toString());
    }
    @Override public Usage departmentUsage(UUID tenant, YearMonth month, UUID department) {
        return jdbc.queryForObject("SELECT coalesce(sum(actual) FILTER(WHERE state='SETTLED'),0), "
            + "coalesce(sum(reserved) FILTER(WHERE state='PENDING'),0) FROM department_spending_reservations WHERE tenant_id=? AND month=? AND department_id=?",
            (r, n) -> new Usage(r.getBigDecimal(1), r.getBigDecimal(2)), tenant, month.toString(), department);
    }
    @Override public void settle(UUID tenant, UUID request, BigDecimal actual) {
        jdbc.update("UPDATE department_spending_reservations SET actual=?,state='SETTLED' WHERE tenant_id=? AND request_id=?",
            actual, tenant, request);
    }
    @Override public List<Reservation> pending(UUID tenant) {
        return jdbc.query("SELECT * FROM department_spending_reservations WHERE tenant_id=? AND state='PENDING' ORDER BY created_at LIMIT 200",
            this::row, tenant);
    }
    @Override public void audit(UUID tenant, UUID actor, String operation, Object before, Object after) {
        jdbc.update("INSERT INTO department_spending_audit(id,tenant_id,actor_id,operation,before_value,after_value) "
            + "VALUES (?,?,?,?,?::jsonb,?::jsonb)", UUID.randomUUID(), tenant, actor, operation,
            mapper.writeValueAsString(before), mapper.writeValueAsString(after));
    }
}
