package br.com.qsoft.governance.engagement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.engagement.domain.CreditsItem;
import br.com.qsoft.governance.engagement.domain.DataStatus;
import br.com.qsoft.governance.engagement.domain.LicenseApplicability;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

@ExtendWith(MockitoExtension.class)
class CreditsSnapshotJdbcAdapterTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private static final Date PERIOD_START = Date.valueOf("2026-07-01");
    private static final String VERSION = "credits-v1";

    private final TenantId tenantId = TenantId.random();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CreditsSnapshotJdbcAdapter adapter = new CreditsSnapshotJdbcAdapter(jdbc);

    @Captor
    private ArgumentCaptor<List<Object[]>> batch;

    @Test
    void findsAndMapsSnapshotUsingTenantPeriodSourceAndVersion() throws SQLException {
        UserId userId = UserId.random();
        BigDecimal usage = new BigDecimal("0.40");
        BigDecimal score = new BigDecimal("64.00");
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("user_id", UUID.class)).thenReturn(userId.value());
        when(row.getBigDecimal("usage_component")).thenReturn(usage);
        when(row.getBigDecimal("training_component")).thenReturn(BigDecimal.ONE);
        when(row.getBigDecimal("license_component")).thenReturn(null);
        when(row.getString("license_applicability")).thenReturn("NOT_APPLICABLE");
        when(row.getBigDecimal("score")).thenReturn(score);
        when(row.getString("data_status")).thenReturn("PARTIAL");
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(row);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class),
            eq(tenantId.value()), eq(PERIOD_START), eq("HISTORICO"), eq(VERSION));

        assertThat(adapter.find(tenantId, SourceKind.HISTORICO, MONTH, VERSION))
            .containsOnlyKeys(userId)
            .containsEntry(userId, new CreditsItem(userId, usage, BigDecimal.ONE, null,
                LicenseApplicability.NOT_APPLICABLE, score, DataStatus.PARTIAL));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowCallbackHandler.class),
            eq(tenantId.value()), eq(PERIOD_START), eq("HISTORICO"), eq(VERSION));
        assertThat(sql.getValue()).contains("FROM engagement_credit_snapshots",
            "WHERE tenant_id = ?", "AND period = ?", "AND source_kind = ?", "AND calculation_version = ?");
    }

    @Test
    void skipsInsertForNullAndEmptyItems() {
        adapter.insertIfAbsent(tenantId, SourceKind.OPERACIONAL, MONTH, VERSION, null);
        adapter.insertIfAbsent(tenantId, SourceKind.OPERACIONAL, MONTH, VERSION, List.of());

        verifyNoInteractions(jdbc);
    }

    @Test
    void insertsAllItemsInOneBatchWithoutOverwritingExistingSnapshots() {
        UserId firstUser = UserId.random();
        UserId secondUser = UserId.random();
        BigDecimal usage = new BigDecimal("0.40");
        BigDecimal score = new BigDecimal("64.00");

        adapter.insertIfAbsent(tenantId, SourceKind.OPERACIONAL, MONTH, VERSION, List.of(
            new CreditsItem(firstUser, usage, BigDecimal.ONE, BigDecimal.ZERO,
                LicenseApplicability.APPLICABLE, score, DataStatus.COMPLETE),
            new CreditsItem(secondUser, BigDecimal.ZERO, BigDecimal.ZERO, null,
                LicenseApplicability.NOT_APPLICABLE, BigDecimal.ZERO, DataStatus.PARTIAL)));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).batchUpdate(sql.capture(), batch.capture());
        assertThat(sql.getValue()).contains("INSERT INTO engagement_credit_snapshots",
            "ON CONFLICT (tenant_id, user_id, period, source_kind, calculation_version) DO NOTHING");
        List<Object[]> rows = batch.getValue();
        assertThat(rows).hasSize(2);
        Object closedAt = rows.getFirst()[11];
        assertThat(closedAt).isInstanceOf(Timestamp.class);
        assertThat(rows.getFirst()).containsExactly(tenantId.value(), firstUser.value(), PERIOD_START,
            "OPERACIONAL", VERSION, usage, BigDecimal.ONE, BigDecimal.ZERO, "APPLICABLE", score,
            "COMPLETE", closedAt);
        assertThat(rows.getLast()).containsExactly(tenantId.value(), secondUser.value(), PERIOD_START,
            "OPERACIONAL", VERSION, BigDecimal.ZERO, BigDecimal.ZERO, null, "NOT_APPLICABLE",
            BigDecimal.ZERO, "PARTIAL", closedAt);
    }
}
