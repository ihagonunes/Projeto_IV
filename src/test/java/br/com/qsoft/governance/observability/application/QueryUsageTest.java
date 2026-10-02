package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryUsageTest {

    private UsageRecordRepository repository;
    private QueryUsage queryUsage;

    @BeforeEach
    void setUp() {
        repository = mock(UsageRecordRepository.class);
        queryUsage = new QueryUsage(repository);
    }

    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> queryUsage.query(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Command é obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        QueryUsageCommand command = new QueryUsageCommand(null, null, null, null, null, 0, 20);

        assertThatThrownBy(() -> queryUsage.query(command))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void deve_normalizarPaginaESizeMinimos() {
        TenantId tenantId = TenantId.random();
        QueryUsageCommand command = new QueryUsageCommand(tenantId, null, null, null, null, -1, 0);

        PageResult<UsageRecord> expectedPage = new PageResult<>(List.of(), 0, 0, 20);
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(expectedPage);

        PageResult<UsageRecord> result = queryUsage.query(command);

        assertThat(result).isEqualTo(expectedPage);

        verify(repository).search(any(), org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void deve_normalizarSizeMaximo() {
        TenantId tenantId = TenantId.random();
        QueryUsageCommand command = new QueryUsageCommand(tenantId, null, null, null, null, 2, 500);

        PageResult<UsageRecord> expectedPage = new PageResult<>(List.of(), 0, 2, 100);
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(expectedPage);

        PageResult<UsageRecord> result = queryUsage.query(command);

        assertThat(result).isEqualTo(expectedPage);

        verify(repository).search(any(), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void deve_executarConsultaComParametrosValidos() {
        TenantId tenantId = TenantId.random();
        UUID deptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = Instant.now();

        QueryUsageCommand command = new QueryUsageCommand(tenantId, deptId, userId, from, to, 1, 50);

        PageResult<UsageRecord> expectedPage = new PageResult<>(List.of(), 0, 1, 50);
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(expectedPage);

        PageResult<UsageRecord> result = queryUsage.query(command);

        assertThat(result).isEqualTo(expectedPage);

        ArgumentCaptor<UsageRecordRepository.UsageRecordFilter> filterCaptor =
            ArgumentCaptor.forClass(UsageRecordRepository.UsageRecordFilter.class);
        verify(repository).search(filterCaptor.capture(), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(50));

        UsageRecordRepository.UsageRecordFilter filter = filterCaptor.getValue();
        assertThat(filter.tenantId()).isEqualTo(tenantId);
        assertThat(filter.departmentId()).isEqualTo(deptId);
        assertThat(filter.userId()).isEqualTo(userId);
        assertThat(filter.from()).isEqualTo(from);
        assertThat(filter.to()).isEqualTo(to);
    }
}
