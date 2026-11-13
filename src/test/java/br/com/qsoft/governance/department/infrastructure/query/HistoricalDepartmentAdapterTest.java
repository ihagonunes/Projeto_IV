package br.com.qsoft.governance.department.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HistoricalDepartmentAdapterTest {

    @Test
    void skipsSqlWhenNoDepartmentsAreInScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThat(new HistoricalDepartmentAdapter(jdbc).legacyIds(TenantId.random(), Set.of())).isEmpty();
        verifyNoInteractions(jdbc);
    }
}
