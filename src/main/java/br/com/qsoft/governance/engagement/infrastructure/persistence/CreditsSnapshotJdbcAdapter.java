package br.com.qsoft.governance.engagement.infrastructure.persistence;

import br.com.qsoft.governance.engagement.application.port.CreditsSnapshotRepository;
import br.com.qsoft.governance.engagement.domain.CreditsItem;
import br.com.qsoft.governance.engagement.domain.DataStatus;
import br.com.qsoft.governance.engagement.domain.LicenseApplicability;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/** Adapter JDBC do snapshot imutável de Credits (V023). */
@Component
public class CreditsSnapshotJdbcAdapter implements CreditsSnapshotRepository {

    private static final String INSERT = """
        INSERT INTO engagement_credit_snapshots
            (tenant_id, user_id, period, source_kind, calculation_version,
             usage_component, training_component, license_component,
             license_applicability, score, data_status, closed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (tenant_id, user_id, period, source_kind, calculation_version) DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    public CreditsSnapshotJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UserId, CreditsItem> find(TenantId tenantId, SourceKind sourceKind,
                                         YearMonth month, String calculationVersion) {
        Map<UserId, CreditsItem> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT user_id, usage_component, training_component, license_component,
                   license_applicability, score, data_status
              FROM engagement_credit_snapshots
             WHERE tenant_id = ?
               AND period = ?
               AND source_kind = ?
               AND calculation_version = ?
            """, (RowCallbackHandler) rs -> result.put(
                UserId.of(rs.getObject("user_id", UUID.class)),
                new CreditsItem(
                    UserId.of(rs.getObject("user_id", UUID.class)),
                    rs.getBigDecimal("usage_component"),
                    rs.getBigDecimal("training_component"),
                    rs.getBigDecimal("license_component"),
                    LicenseApplicability.valueOf(rs.getString("license_applicability")),
                    rs.getBigDecimal("score"),
                    DataStatus.valueOf(rs.getString("data_status")))),
            tenantId.value(), java.sql.Date.valueOf(month.atDay(1)),
            sourceKind.name(), calculationVersion);
        return result;
    }

    @Override
    public void insertIfAbsent(TenantId tenantId, SourceKind sourceKind, YearMonth month,
                               String calculationVersion, List<CreditsItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Timestamp closedAt = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(INSERT, items.stream().map(item -> new Object[]{
            tenantId.value(),
            item.userId().value(),
            java.sql.Date.valueOf(month.atDay(1)),
            sourceKind.name(),
            calculationVersion,
            item.usage(),
            item.training(),
            item.license(),
            item.licenseApplicability().name(),
            item.score(),
            item.dataStatus().name(),
            closedAt
        }).toList());
    }
}
