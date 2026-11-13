package br.com.qsoft.governance.observability.infrastructure.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.observability.application.ProjectDepartmentBudget;
import br.com.qsoft.governance.observability.application.ProjectionDepartmentNotFoundException;
import br.com.qsoft.governance.observability.application.UsageVisibilityDeniedException;
import br.com.qsoft.governance.observability.domain.BudgetProjection;
import br.com.qsoft.governance.observability.domain.BudgetRecommendation;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Contrato HTTP e mapeamento de erros da projeção (SPEC-09 §1). */
class BudgetProjectionControllerTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();
    private final UUID departmentId = UUID.randomUUID();
    private ProjectDepartmentBudget projectDepartmentBudget;
    private AuthorizationQuery authorizationQuery;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        projectDepartmentBudget = mock(ProjectDepartmentBudget.class);
        authorizationQuery = mock(AuthorizationQuery.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BudgetProjectionController(projectDepartmentBudget, authorizationQuery))
            .setControllerAdvice(new ObservabilityApiExceptionHandler())
            .build();
        TenantContextHolder.set(new TenantContext(tenantId, userId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void allow() {
        when(authorizationQuery.isAllowed(eq(tenantId), eq(userId), eq(Resource.USAGE),
            eq(Action.READ), any(AccessTarget.DepartmentTarget.class))).thenReturn(true);
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());
    }

    private BudgetProjection projection() {
        return new BudgetProjection(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 27), 14,
            new BigDecimal("1000.00"), false, 80, new BigDecimal("640.00"),
            new BigDecimal("28.571429"), 4, new BigDecimal("754.29"),
            new BigDecimal("75.43"), null, BudgetRecommendation.DENTRO_DO_ORCAMENTO,
            "projection-v1");
    }

    @Test
    void defaultsMissingMonthAndLegacyCalculationVersion() throws Exception {
        allow();
        when(projectDepartmentBudget.query(eq(tenantId), eq(departmentId), any(YearMonth.class), eq(14),
            eq(new VisibilityScope.TenantWide()))).thenAnswer(invocation -> {
                YearMonth month = invocation.getArgument(2);
                return new BudgetProjection(month, month.atDay(1), 14, BigDecimal.TEN, false, 80,
                    BigDecimal.ZERO, BigDecimal.ZERO, 30, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    BudgetRecommendation.DENTRO_DO_ORCAMENTO, null);
            });
        YearMonth before = YearMonth.now(java.time.ZoneOffset.UTC);
        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao").param("departmentId", departmentId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.calculationVersion").value(BudgetProjection.CALCULATION_VERSION))
            .andExpect(jsonPath("$.mes").value(org.hamcrest.Matchers.isOneOf(
                before.toString(), YearMonth.now(java.time.ZoneOffset.UTC).toString())));
    }

    @Test
    void devolveContratoDaProjecao() throws Exception {
        allow();
        when(projectDepartmentBudget.query(tenantId, departmentId, YearMonth.of(2026, 10), 14,
            new VisibilityScope.TenantWide())).thenReturn(projection());

        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("departmentId", departmentId.toString())
                .param("mes", "2026-10")
                .param("janelaDias", "14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
            .andExpect(jsonPath("$.mes").value("2026-10"))
            .andExpect(jsonPath("$.hoje").value("2026-10-27"))
            .andExpect(jsonPath("$.janelaDias").value(14))
            .andExpect(jsonPath("$.orcamento").value(1000.00))
            .andExpect(jsonPath("$.orcamentoPadrao").value(false))
            .andExpect(jsonPath("$.limiarAlerta").value(80))
            .andExpect(jsonPath("$.gastoAteHoje").value(640.00))
            .andExpect(jsonPath("$.mediaDiaria").value(28.571429))
            .andExpect(jsonPath("$.diasRestantes").value(4))
            .andExpect(jsonPath("$.projetado").value(754.29))
            .andExpect(jsonPath("$.pctProjetado").value(75.43))
            .andExpect(jsonPath("$.diaEsgotamento").value(nullValue()))
            .andExpect(jsonPath("$.recomendacao").value("DENTRO_DO_ORCAMENTO"))
            .andExpect(jsonPath("$.tentativasInconclusivasNaJanela").value(nullValue()))
            .andExpect(jsonPath("$.calculationVersion").value("projection-v1"));
    }

    @Test
    void negaDepartamentoForaDaConcessaoSemChamarOCalculo() throws Exception {
        when(authorizationQuery.isAllowed(eq(tenantId), eq(userId), eq(Resource.USAGE),
            eq(Action.READ), any(AccessTarget.DepartmentTarget.class))).thenReturn(false);

        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("departmentId", departmentId.toString())
                .param("mes", "2026-10"))
            .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(projectDepartmentBudget);
    }

    @Test
    void departamentoInexistenteRetorna404SemDetalhesInternos() throws Exception {
        allow();
        when(projectDepartmentBudget.query(eq(tenantId), eq(departmentId),
            eq(YearMonth.of(2026, 10)), eq(14), any()))
            .thenThrow(new ProjectionDepartmentNotFoundException());

        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("departmentId", departmentId.toString())
                .param("mes", "2026-10"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Departamento não encontrado."));
    }

    @Test
    void erroDeValidacaoDoCasoDeUsoRetorna400() throws Exception {
        allow();
        when(projectDepartmentBudget.query(eq(tenantId), eq(departmentId),
            eq(YearMonth.of(2026, 10)), eq(6), any()))
            .thenThrow(new IllegalArgumentException("janelaDias deve estar entre 7 e 60."));

        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("departmentId", departmentId.toString())
                .param("mes", "2026-10")
                .param("janelaDias", "6"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("janelaDias deve estar entre 7 e 60."));
    }

    @Test
    void mesInvalidoRetorna400() throws Exception {
        allow();

        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("departmentId", departmentId.toString())
                .param("mes", "2026-13"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void semDepartamentoRetorna400() throws Exception {
        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("mes", "2026-10"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void escopoNoneAindaRetorna403QuandoOIsAllowedDivergir() throws Exception {
        when(authorizationQuery.isAllowed(eq(tenantId), eq(userId), eq(Resource.USAGE),
            eq(Action.READ), any(AccessTarget.DepartmentTarget.class))).thenReturn(true);
        when(authorizationQuery.visibilityFor(tenantId, userId, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.None());
        when(projectDepartmentBudget.query(eq(tenantId), eq(departmentId),
            eq(YearMonth.of(2026, 10)), eq(14), any()))
            .thenThrow(new UsageVisibilityDeniedException());

        mockMvc.perform(get("/api/v1/observability/orcamentos/projecao")
                .param("departmentId", departmentId.toString())
                .param("mes", "2026-10"))
            .andExpect(status().isForbidden());
    }
}
