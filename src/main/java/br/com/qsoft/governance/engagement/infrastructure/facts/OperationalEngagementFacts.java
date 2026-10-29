package br.com.qsoft.governance.engagement.infrastructure.facts;

import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.domain.TrainingFact;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.observability.api.query.UsageActivityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fatos operacionais: IAM + observability + tabelas V023 (ADR-40 §4). */
@Component
public class OperationalEngagementFacts implements EngagementFactsPort {

    private final IdentityQuery identityQuery;
    private final UsageActivityQuery usageActivityQuery;
    private final JdbcTemplate jdbcTemplate;

    public OperationalEngagementFacts(IdentityQuery identityQuery,
                                      UsageActivityQuery usageActivityQuery,
                                      JdbcTemplate jdbcTemplate) {
        this.identityQuery = identityQuery;
        this.usageActivityQuery = usageActivityQuery;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public EngagementFacts load(TenantId tenantId, LocalDate ate) {
        LocalDate reference = ate == null ? LocalDate.now(ZoneOffset.UTC) : ate;
        List<UserContext> contexts = identityQuery.eligibleUsers(tenantId);
        Set<UserId> eligible = new LinkedHashSet<>();
        contexts.forEach(context -> eligible.add(context.userId()));

        LocalDate from = reference.minusDays(29);
        Map<UserId, Set<LocalDate>> activeDays = usageActivityQuery.activeDays(
            tenantId, eligible, from, reference);

        Map<UserId, List<LicenseFact>> licenses = loadLicenses(tenantId, eligible);
        Map<UserId, List<TrainingFact>> trainings = loadTrainings(tenantId, eligible);
        Coverage coverage = new Coverage(eligible.size(), eligible.size(), 0, 0);
        return new EngagementFacts(eligible, activeDays, licenses, trainings, coverage,
            true, true);
    }

    @Override
    public Optional<LocalDate> latestCoverage(TenantId tenantId) {
        return Optional.empty();
    }

    private Map<UserId, List<LicenseFact>> loadLicenses(TenantId tenantId,
                                                        Set<UserId> eligible) {
        Map<UserId, List<LicenseFact>> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT user_id, ai_tool_id, assigned_at, expires_at, status
              FROM engagement_ai_licenses
             WHERE tenant_id = ?
            """, rs -> {
                UserId userId = UserId.of(rs.getObject("user_id", UUID.class));
                if (!eligible.contains(userId)) {
                    return;
                }
                result.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(
                    new LicenseFact(
                        rs.getObject("ai_tool_id", UUID.class),
                        rs.getDate("assigned_at").toLocalDate(),
                        rs.getDate("expires_at") == null
                            ? null : rs.getDate("expires_at").toLocalDate(),
                        "ACTIVE".equalsIgnoreCase(rs.getString("status"))));
            }, tenantId.value());
        return result;
    }

    private Map<UserId, List<TrainingFact>> loadTrainings(TenantId tenantId,
                                                          Set<UserId> eligible) {
        Map<UserId, List<TrainingFact>> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT user_id, course_name, completed_at
              FROM engagement_training_records
             WHERE tenant_id = ?
               AND status = 'COMPLETED'
               AND completed_at IS NOT NULL
            """, rs -> {
                UserId userId = UserId.of(rs.getObject("user_id", UUID.class));
                if (!eligible.contains(userId)) {
                    return;
                }
                result.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(
                    new TrainingFact(rs.getString("course_name"),
                        rs.getDate("completed_at").toLocalDate()));
            }, tenantId.value());
        return result;
    }
}
