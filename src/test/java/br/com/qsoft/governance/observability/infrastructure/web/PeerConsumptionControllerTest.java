package br.com.qsoft.governance.observability.infrastructure.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.observability.application.QueryPeerConsumption;
import br.com.qsoft.governance.observability.application.port.PeerConsumptionQuery.Average;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PeerConsumptionControllerTest {
    @Test
    void returnsAveragesOrExplicitNullsForSuppressedCohort() throws Exception {
        TenantId tenant = TenantId.random();
        UserId user = UserId.random();
        YearMonth month = YearMonth.of(2026, 7);
        var query = mock(QueryPeerConsumption.class);
        when(query.average(tenant, user, month, QueryPeerConsumption.Source.OPERACIONAL))
            .thenReturn(new Average(BigDecimal.TEN, BigDecimal.ONE));
        var mvc = MockMvcBuilders.standaloneSetup(new PeerConsumptionController(query)).build();

        TenantContextHolder.set(new TenantContext(tenant, user));
        try {
            var request = "/api/v1/observability/metrics/me/peer-consumption";
            mvc.perform(get(request).param("month", "2026-07").param("source", "OPERACIONAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageTokens").value(10))
                .andExpect(jsonPath("$.averageRequests").value(1));
            mvc.perform(get(request).param("month", "2026-07").param("source", "HISTORICO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageTokens").value(nullValue()))
                .andExpect(jsonPath("$.averageRequests").value(nullValue()));
        } finally {
            TenantContextHolder.clear();
        }
    }
}
