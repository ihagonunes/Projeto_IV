package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservabilityCommandTest {

    @Test
    void deve_testarAcessoresDeDashboardQueryCommand() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID deptId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = Instant.now();

        DashboardQueryCommand cmd = new DashboardQueryCommand(
            tenantId, userId, deptId, from, to, UsageMetricsRepository.Granularity.DAY);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.userId()).isEqualTo(userId);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
        assertThat(cmd.from()).isEqualTo(from);
        assertThat(cmd.to()).isEqualTo(to);
        assertThat(cmd.granularity()).isEqualTo(UsageMetricsRepository.Granularity.DAY);
    }

    @Test
    void deve_testarAcessoresDeQueryUsageCommand() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = Instant.now();

        QueryUsageCommand cmd = new QueryUsageCommand(
            tenantId, UserId.random(), deptId, userId, from, to, 1, 20);

        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.departmentId()).isEqualTo(deptId);
        assertThat(cmd.userId()).isEqualTo(userId);
        assertThat(cmd.from()).isEqualTo(from);
        assertThat(cmd.to()).isEqualTo(to);
        assertThat(cmd.page()).isEqualTo(1);
        assertThat(cmd.size()).isEqualTo(20);
    }

    @Test
    void deve_testarAcessoresDeUsageMetricsSeries() {
        Instant from = Instant.now();
        Instant to = Instant.now();

        UsageMetricsSeries series = new UsageMetricsSeries("DAY", from, to, 15L, List.of());

        assertThat(series.granularity()).isEqualTo("DAY");
        assertThat(series.from()).isEqualTo(from);
        assertThat(series.to()).isEqualTo(to);
        assertThat(series.activeUsers()).isEqualTo(15L);
        assertThat(series.series()).isEmpty();
    }
}
