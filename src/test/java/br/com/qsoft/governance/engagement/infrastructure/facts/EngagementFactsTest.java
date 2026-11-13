package br.com.qsoft.governance.engagement.infrastructure.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.domain.TrainingFact;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.HistoricalIdentityQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.observability.api.query.UsageActivityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

class EngagementFactsTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 7, 18);
    private static final String EMPLOYEE = "employee-1";
    private static final String HISTORICAL_USAGE = "historical.ai_usage_logs";
    private static final String HISTORICAL_LICENSES = "historical.ai_licenses";
    private static final String HISTORICAL_TRAININGS = "historical.training_records";
    private static final String OPERATIONAL_LICENSES = "engagement_ai_licenses";
    private static final String OPERATIONAL_TRAININGS = "engagement_training_records";

    private final TenantId tenant = TenantId.random();
    private final String company = tenant.value().toString();
    private final UserId member = UserId.random();
    private final UserId unlinked = UserId.random();
    private final IdentityQuery identity = mock(IdentityQuery.class);
    private final HistoricalIdentityQuery historicalIdentity = mock(HistoricalIdentityQuery.class);
    private final UsageActivityQuery usage = mock(UsageActivityQuery.class);
    private final RecordingJdbc jdbc = new RecordingJdbc();
    private final OperationalEngagementFacts operational = new OperationalEngagementFacts(identity, usage, jdbc);
    private final HistoricalEngagementFacts historical = new HistoricalEngagementFacts(identity, historicalIdentity, jdbc);

    @Test
    void operationalFactsUseTenantAndEligibleUsersAndMapLicensesAndCompletedTraining() {
        UserId ineligible = UserId.random();
        UUID tool = UUID.randomUUID();
        LocalDate expiry = REFERENCE.plusDays(5);
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(member)));
        when(usage.activeDays(tenant, Set.of(member), REFERENCE.minusDays(29), REFERENCE))
            .thenReturn(Map.of(member, Set.of(REFERENCE)));
        jdbc.rows.put(OPERATIONAL_LICENSES, List.of(
            row(Map.of("user_id", member.value(), "ai_tool_id", tool, "assigned_at", Date.valueOf(REFERENCE),
                "status", "active")),
            row(Map.of("user_id", member.value(), "assigned_at", Date.valueOf(REFERENCE),
                "expires_at", Date.valueOf(expiry), "status", "REVOKED")),
            row(Map.of("user_id", ineligible.value()))));
        jdbc.rows.put(OPERATIONAL_TRAININGS, List.of(
            row(Map.of("user_id", member.value(), "course_name", "Governança",
                "completed_at", Date.valueOf(REFERENCE))),
            row(Map.of("user_id", ineligible.value()))));

        EngagementFacts facts = operational.load(tenant, REFERENCE);

        assertThat(facts).isEqualTo(new EngagementFacts(Set.of(member), Map.of(member, Set.of(REFERENCE)),
            Map.of(member, List.of(new LicenseFact(tool, REFERENCE, null, true),
                new LicenseFact(null, REFERENCE, expiry, false))),
            Map.of(member, List.of(new TrainingFact("Governança", REFERENCE))), new Coverage(1, 1, 0, 0)));
        assertThat(jdbc.queries).hasSize(2);
        assertQuery(0, OPERATIONAL_LICENSES, "tenant_id = ?", tenant.value());
        assertQuery(1, OPERATIONAL_TRAININGS, "tenant_id = ?", tenant.value());
        assertThat(jdbc.queries.get(1).sql()).contains("status = 'COMPLETED'", "completed_at IS NOT NULL");
    }

    @Test
    void operationalFactsDefaultToTodayInUtcAndHaveNoHistoricalCoverageDate() {
        when(identity.eligibleUsers(tenant)).thenReturn(List.of());
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        EngagementFacts facts = operational.load(tenant, null);

        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        verify(usage).activeDays(eq(tenant), eq(Set.of()), start.capture(), end.capture());
        assertThat(end.getValue()).isIn(before, LocalDate.now(ZoneOffset.UTC));
        assertThat(start.getValue()).isEqualTo(end.getValue().minusDays(29));
        assertThat(facts).isEqualTo(new EngagementFacts(Set.of(), Map.of(), Map.of(), Map.of(),
            new Coverage(0, 0, 0, 0)));
        assertThat(operational.latestCoverage(tenant)).isEmpty();
    }

    @Test
    void historicalFactsSkipJdbcWhenNoEligibleUsersAreLinked() {
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(member)));
        when(historicalIdentity.employeeIds(tenant, Set.of(member))).thenReturn(Map.of());

        assertThat(historical.load(tenant, null)).isEqualTo(new EngagementFacts(
            Set.of(), Map.of(), Map.of(), Map.of(), new Coverage(1, 0, 0, 0)));
        assertThat(jdbc.queries).isEmpty();
    }

    @Test
    void historicalFactsNormalizeStatusesAndOptionalValuesAndIgnoreUnlinkedRows() {
        linkMember();
        UUID tool = UUID.randomUUID();
        jdbc.rows.put(HISTORICAL_USAGE, List.of(
            row(Map.of("employee_id", EMPLOYEE, "day", Date.valueOf(REFERENCE))),
            row(Map.of("employee_id", "outsider"))));
        jdbc.rows.put(HISTORICAL_LICENSES, List.of(
            row(Map.of("employee_id", EMPLOYEE, "license_status", " active ", "ai_tool_id", tool.toString(),
                "assigned_date", "2026-07-01T00:00:00Z", "expiration_date", "2026-07-31")),
            row(Map.of("employee_id", EMPLOYEE, "license_status", "EXPIRED", "ai_tool_id", "bad-uuid",
                "assigned_date", "bad-date", "expiration_date", " ")),
            row(Map.of("employee_id", EMPLOYEE, "license_status", "REVOKED", "ai_tool_id", " ")),
            row(Map.of("employee_id", EMPLOYEE, "license_status", "ACTIVE")),
            row(Map.of("employee_id", EMPLOYEE, "license_status", "UNKNOWN")),
            row(Map.of("employee_id", "outsider", "license_status", "ACTIVE"))));
        jdbc.rows.put(HISTORICAL_TRAININGS, List.of(
            row(Map.of("employee_id", EMPLOYEE, "completion_status", " completed ",
                "course_name", "Governança", "completion_date", "2026-07-18T12:00:00Z")),
            row(Map.of("employee_id", EMPLOYEE, "completion_status", "ENROLLED")),
            row(Map.of("employee_id", EMPLOYEE)),
            row(Map.of("employee_id", "outsider", "completion_status", "COMPLETED"))));

        EngagementFacts facts = historical.load(tenant, REFERENCE);

        assertThat(facts).isEqualTo(new EngagementFacts(Set.of(member), Map.of(member, Set.of(REFERENCE)),
            Map.of(member, List.of(new LicenseFact(tool, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), true),
                new LicenseFact(null, null, null, false), new LicenseFact(null, null, null, false),
                new LicenseFact(null, null, null, true))),
            Map.of(member, List.of(new TrainingFact("Governança", REFERENCE))), new Coverage(2, 1, 1, 1)));
        assertThat(jdbc.queries).hasSize(3);
        assertQuery(0, HISTORICAL_USAGE, "company_id = ?", company, EMPLOYEE, "2026-06-19", "2026-07-19");
        assertThat(jdbc.queries.getFirst().sql()).contains("request_count > 0", "\"timestamp\" >= ?", "\"timestamp\" < ?");
        assertQuery(1, HISTORICAL_LICENSES, "company_id = ?", company, EMPLOYEE);
        assertQuery(2, HISTORICAL_TRAININGS, "company_id = ?", company, EMPLOYEE);
    }

    @Test
    void historicalFactsUseLatestTenantCoverageWhenReferenceIsOmitted() {
        linkMember();
        jdbc.coverageRow = row(Map.of("day", Date.valueOf(REFERENCE)));

        historical.load(tenant, null);

        assertQuery(0, HISTORICAL_USAGE, "company_id = ?", company);
        assertThat(jdbc.queries.getFirst().sql()).contains("SELECT max(");
        assertQuery(1, HISTORICAL_USAGE, "company_id = ?", company, EMPLOYEE, "2026-06-19", "2026-07-19");
    }

    @Test
    void historicalFactsFallBackToUtcTodayWhenTenantHasNoCoverage() {
        linkMember();
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        historical.load(tenant, null);

        List<Object> parameters = jdbc.queries.get(1).parameters();
        LocalDate reference = LocalDate.parse((String) parameters.getLast()).minusDays(1);
        assertThat(reference).isIn(before, LocalDate.now(ZoneOffset.UTC));
        assertQuery(1, HISTORICAL_USAGE, "company_id = ?", company, EMPLOYEE,
            reference.minusDays(29).toString(), reference.plusDays(1).toString());
    }

    @Test
    void historicalFactsBatchDistinctEmployeesAndKeepTenantInEveryQuery() {
        Map<UserId, String> links = new LinkedHashMap<>();
        for (int index = 0; index < 5_001; index++) {
            links.put(UserId.random(), "employee-" + index);
        }
        links.put(UserId.random(), "employee-0");
        when(identity.eligibleUsers(tenant)).thenReturn(links.keySet().stream().map(this::context).toList());
        when(historicalIdentity.employeeIds(eq(tenant), any())).thenReturn(links);

        historical.load(tenant, REFERENCE);

        assertThat(jdbc.queries).hasSize(6);
        assertBatches(0, HISTORICAL_USAGE, 2);
        assertBatches(2, HISTORICAL_LICENSES, 0);
        assertBatches(4, HISTORICAL_TRAININGS, 0);
    }

    private void assertBatches(int firstQuery, String table, int trailingParameters) {
        List<Object> employees = new ArrayList<>();
        for (int size : List.of(5_000, 1)) {
            JdbcQuery query = jdbc.queries.get(firstQuery++);
            assertThat(query.sql()).contains("FROM " + table, "company_id = ?", "employee_id IN (");
            assertThat(query.parameters()).hasSize(1 + size + trailingParameters).startsWith(company);
            employees.addAll(query.parameters().subList(1, 1 + size));
        }
        assertThat(employees).hasSize(5_001).doesNotHaveDuplicates();
    }

    private void assertQuery(int index, String table, String filter, Object... parameters) {
        JdbcQuery query = jdbc.queries.get(index);
        assertThat(query.sql()).contains("FROM " + table, filter);
        assertThat(query.parameters()).containsExactly(parameters);
    }

    private void linkMember() {
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(member), context(unlinked)));
        when(historicalIdentity.employeeIds(tenant, Set.of(member, unlinked))).thenReturn(Map.of(member, EMPLOYEE));
    }

    private UserContext context(UserId user) {
        return new UserContext(user, tenant, "COLLABORATOR", null, "ACTIVE", "Pessoa de teste");
    }

    private static ResultSet row(Map<String, ?> columns) {
        return mock(ResultSet.class, invocation -> columns.get(invocation.getArgument(0)));
    }

    private record JdbcQuery(String sql, List<Object> parameters) {
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        private final List<JdbcQuery> queries = new ArrayList<>();
        private final Map<String, List<ResultSet>> rows = new LinkedHashMap<>();
        private ResultSet coverageRow = row(Map.of());

        @Override
        public void query(String sql, RowCallbackHandler callback, Object... parameters) {
            queries.add(new JdbcQuery(sql, List.of(parameters)));
            List<ResultSet> result = rows.entrySet().stream()
                .filter(entry -> sql.contains("FROM " + entry.getKey()))
                .findFirst().map(Map.Entry::getValue).orElse(List.of());
            for (ResultSet row : result) {
                try {
                    callback.processRow(row);
                } catch (SQLException ex) {
                    throw new AssertionError(ex);
                }
            }
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters) {
            queries.add(new JdbcQuery(sql, List.of(parameters)));
            try {
                return java.util.Collections.singletonList(mapper.mapRow(coverageRow, 0));
            } catch (SQLException ex) {
                throw new AssertionError(ex);
            }
        }
    }
}
