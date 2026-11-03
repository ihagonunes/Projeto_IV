package br.com.qsoft.governance.engagement.infrastructure.facts;

import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.domain.TrainingFact;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fatos históricos (NexoCode) com {@code company_id} explícito (ADR-26/ADR-40). */
@Component
public class HistoricalEngagementFacts implements EngagementFactsPort {

    private static final int BATCH_SIZE = 5_000;
    private static final Map<String, Boolean> LICENSE_STATUS = Map.of(
        "ACTIVE", true,
        "EXPIRED", false,
        "REVOKED", false);
    private static final Set<String> KNOWN_TRAINING_STATUS = Set.of(
        "COMPLETED", "ENROLLED", "DROPPED", "IN_PROGRESS", "CANCELLED", "NOT_COMPLETED");

    private final IdentityQuery identityQuery;
    private final HistoricalIdentityQuery historicalIdentityQuery;
    private final JdbcTemplate jdbcTemplate;

    public HistoricalEngagementFacts(IdentityQuery identityQuery,
                                     HistoricalIdentityQuery historicalIdentityQuery,
                                     JdbcTemplate jdbcTemplate) {
        this.identityQuery = identityQuery;
        this.historicalIdentityQuery = historicalIdentityQuery;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public EngagementFacts load(TenantId tenantId, LocalDate ate) {
        List<UserContext> contexts = identityQuery.eligibleUsers(tenantId);
        Set<UserId> eligible = contexts.stream().map(UserContext::userId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UserId, String> links = historicalIdentityQuery.employeeIds(tenantId, eligible);
        if (links.isEmpty()) {
            return new EngagementFacts(Set.of(), Map.of(), Map.of(), Map.of(),
                new Coverage(eligible.size(), 0, 0, 0), true, true);
        }

        LocalDate reference = ate == null
            ? latestCoverage(tenantId).orElse(LocalDate.now(ZoneOffset.UTC)) : ate;
        LocalDate from = reference.minusDays(29);
        Map<String, UserId> byEmployee = links.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getValue, Map.Entry::getKey, (left, right) -> left));
        List<String> employees = new ArrayList<>(byEmployee.keySet());

        Map<UserId, Set<LocalDate>> activeDays = loadActiveDays(
            tenantId, byEmployee, employees, from, reference);
        int[] unmapped = new int[2];
        Map<UserId, List<LicenseFact>> licenses = loadLicenses(
            tenantId, byEmployee, employees, unmapped);
        Map<UserId, List<TrainingFact>> trainings = loadTrainings(
            tenantId, byEmployee, employees, unmapped);

        Coverage coverage = new Coverage(eligible.size(), links.size(),
            unmapped[0], unmapped[1]);
        return new EngagementFacts(links.keySet(), activeDays, licenses, trainings, coverage,
            true, true);
    }

    @Override
    public Optional<LocalDate> latestCoverage(TenantId tenantId) {
        List<LocalDate> days = jdbcTemplate.query("""
            SELECT max(left("timestamp", 10)::date) AS day
              FROM historical.ai_usage_logs
             WHERE company_id = ?
            """, (rs, rowNum) -> {
                java.sql.Date day = rs.getDate("day");
                return day == null ? null : day.toLocalDate();
            }, tenantId.value().toString());
        return days.stream().filter(Objects::nonNull).findFirst();
    }

    private Map<UserId, Set<LocalDate>> loadActiveDays(TenantId tenantId,
                                                       Map<String, UserId> byEmployee,
                                                       List<String> employees,
                                                       LocalDate from, LocalDate to) {
        Map<UserId, Set<LocalDate>> result = new LinkedHashMap<>();
        for (List<String> batch : batches(employees)) {
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            List<Object> params = new ArrayList<>(batch.size() + 4);
            params.add(tenantId.value().toString());
            params.addAll(batch);
            params.add(from.toString());
            params.add(to.plusDays(1).toString());
            jdbcTemplate.query("""
                SELECT employee_id, left("timestamp", 10)::date AS day
                  FROM historical.ai_usage_logs
                 WHERE company_id = ?
                   AND employee_id IN (%s)
                   AND request_count > 0
                   AND "timestamp" >= ?
                   AND "timestamp" < ?
                 GROUP BY employee_id, day
                """.formatted(placeholders), rs -> {
                    UserId userId = byEmployee.get(rs.getString("employee_id"));
                    if (userId != null) {
                        result.computeIfAbsent(userId, ignored -> new LinkedHashSet<>())
                            .add(rs.getDate("day").toLocalDate());
                    }
                }, params.toArray());
        }
        return result;
    }

    private Map<UserId, List<LicenseFact>> loadLicenses(TenantId tenantId,
                                                        Map<String, UserId> byEmployee,
                                                        List<String> employees,
                                                        int[] unmapped) {
        Map<UserId, List<LicenseFact>> result = new LinkedHashMap<>();
        for (List<String> batch : batches(employees)) {
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            List<Object> params = new ArrayList<>(batch.size() + 1);
            params.add(tenantId.value().toString());
            params.addAll(batch);
            jdbcTemplate.query("""
                SELECT employee_id, ai_tool_id, assigned_date, expiration_date, license_status
                  FROM historical.ai_licenses
                 WHERE company_id = ?
                   AND employee_id IN (%s)
                """.formatted(placeholders), rs -> {
                    UserId userId = byEmployee.get(rs.getString("employee_id"));
                    Boolean active = LICENSE_STATUS.get(normalize(rs.getString("license_status")));
                    if (userId == null || active == null) {
                        if (userId != null) {
                            unmapped[0]++;
                        }
                        return;
                    }
                    result.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(
                        new LicenseFact(
                            parseUuid(rs.getString("ai_tool_id")),
                            parseDate(rs.getString("assigned_date")),
                            parseDate(rs.getString("expiration_date")),
                            active));
                }, params.toArray());
        }
        return result;
    }

    private Map<UserId, List<TrainingFact>> loadTrainings(TenantId tenantId,
                                                          Map<String, UserId> byEmployee,
                                                          List<String> employees,
                                                          int[] unmapped) {
        Map<UserId, List<TrainingFact>> result = new LinkedHashMap<>();
        for (List<String> batch : batches(employees)) {
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            List<Object> params = new ArrayList<>(batch.size() + 1);
            params.add(tenantId.value().toString());
            params.addAll(batch);
            jdbcTemplate.query("""
                SELECT employee_id, course_name, completion_date, completion_status
                  FROM historical.training_records
                 WHERE company_id = ?
                   AND employee_id IN (%s)
                """.formatted(placeholders), rs -> {
                    UserId userId = byEmployee.get(rs.getString("employee_id"));
                    String status = normalize(rs.getString("completion_status"));
                    if (userId == null) {
                        return;
                    }
                    if (!KNOWN_TRAINING_STATUS.contains(status)) {
                        unmapped[1]++;
                        return;
                    }
                    if ("COMPLETED".equals(status)) {
                        result.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(
                            new TrainingFact(rs.getString("course_name"),
                                parseDate(rs.getString("completion_date"))));
                    }
                }, params.toArray());
        }
        return result;
    }

    private static List<List<String>> batches(List<String> values) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < values.size(); start += BATCH_SIZE) {
            batches.add(values.subList(start, Math.min(start + BATCH_SIZE, values.size())));
        }
        return batches;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
