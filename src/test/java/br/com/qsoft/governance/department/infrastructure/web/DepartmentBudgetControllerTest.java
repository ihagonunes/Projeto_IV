package br.com.qsoft.governance.department.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.department.application.ListMonthlyBudgets;
import br.com.qsoft.governance.department.application.UpsertMonthlyBudget;
import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DepartmentBudgetControllerTest {

    private static final String BASE = "/api/v1/departamentos/{id}/orcamentos";
    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private static final BigDecimal AMOUNT = new BigDecimal("1500");
    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UUID department = UUID.randomUUID();
    private final AccessTarget target = new AccessTarget.DepartmentTarget(department);
    private final UpsertMonthlyBudget upsert = mock(UpsertMonthlyBudget.class);
    private final ListMonthlyBudgets list = mock(ListMonthlyBudgets.class);
    private final AuthorizationQuery auth = mock(AuthorizationQuery.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DepartmentBudgetController(upsert, list, auth))
        .setControllerAdvice(new DepartmentApiExceptionHandler()).build();

    @BeforeEach
    void setContext() {
        TenantContextHolder.set(new TenantContext(tenant, actor));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 65)
    void upsertsBudgetWithDefaultOrExplicitThreshold(Integer requested) throws Exception {
        allowManage();
        int threshold = requested == null ? 80 : requested;
        when(upsert.upsert(new UpsertMonthlyBudget.Command(tenant, department, MONTH, AMOUNT, threshold, actor)))
            .thenReturn(budget(threshold));

        mvc.perform(put(BASE + "/{mes}", department, MONTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1500,\"alertThresholdPct\":" + requested + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mes").value("2026-07"))
            .andExpect(jsonPath("$.departmentId").value(department.toString()))
            .andExpect(jsonPath("$.valor").value(1500))
            .andExpect(jsonPath("$.limiarAlerta").value(threshold))
            .andExpect(jsonPath("$.atualizadoPor").value(actor.toString()));
    }

    @Test
    void listsBudgetHistoryForAuthorizedDepartmentAndValidatesDates() throws Exception {
        when(auth.isAllowed(tenant, actor, Resource.DEPARTMENT, Action.READ, target)).thenReturn(true);
        when(list.list(tenant, department, MONTH, MONTH.plusMonths(1))).thenReturn(List.of(budget(80)));

        mvc.perform(get(BASE, department).param("de", "2026-07").param("ate", "2026-08"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].mes").value("2026-07"));
        mvc.perform(get(BASE, department).param("de", "2026-13").param("ate", "2026-08"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("mes inválido (use AAAA-MM): 2026-13."));
    }

    @Test
    void rejectsRequestsOutsideActorScopeBeforeAccessingBudget() throws Exception {
        mvc.perform(put(BASE + "/{mes}", department, MONTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1500}")).andExpect(status().isForbidden());
        mvc.perform(get(BASE, department).param("de", "2026-07").param("ate", "2026-08"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(upsert, list);
    }

    @Test
    void rejectsMissingAmountWithoutWritingBudget() throws Exception {
        allowManage();
        mvc.perform(put(BASE + "/{mes}", department, MONTH).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("amount é obrigatório."));
        verifyNoInteractions(upsert);
    }

    private void allowManage() {
        when(auth.isAllowed(tenant, actor, Resource.DEPARTMENT_BUDGET, Action.MANAGE, target)).thenReturn(true);
    }

    private MonthlyBudget budget(int threshold) {
        return MonthlyBudget.create(UUID.randomUUID(), tenant, department, MONTH, AMOUNT, threshold, actor.value());
    }
}
