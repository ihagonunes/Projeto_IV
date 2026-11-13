package br.com.qsoft.governance.engagement.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.engagement.application.AdoptionCommand;
import br.com.qsoft.governance.engagement.application.AdoptionReport;
import br.com.qsoft.governance.engagement.application.CreditsCommand;
import br.com.qsoft.governance.engagement.application.CreditsReport;
import br.com.qsoft.governance.engagement.application.EngagementAccessDeniedException;
import br.com.qsoft.governance.engagement.application.QueryAdoption;
import br.com.qsoft.governance.engagement.application.QueryCredits;
import br.com.qsoft.governance.engagement.application.QueryTrainingCross;
import br.com.qsoft.governance.engagement.application.TrainingCrossReport;
import br.com.qsoft.governance.engagement.domain.AdoptionResult;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.DataStatus;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EngagementWebTest {

    private static final String BASE = "/api/v1/engajamento";
    private static final String ADOPTION = "/adocao";
    private static final String TRAINING = "/treinamentos/cruzamento";
    private static final String CREDITS = "/creditos";
    private static final LocalDate AS_OF = LocalDate.of(2026, 7, 18);
    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private static final Coverage COVERAGE = new Coverage(9, 8, 2, 3);

    private final TenantId tenantId = TenantId.random();
    private final UserId actor = UserId.random();
    private final UserId member = UserId.random();
    private final UUID departmentId = UUID.randomUUID();
    private final QueryAdoption adoption = mock(QueryAdoption.class);
    private final QueryTrainingCross training = mock(QueryTrainingCross.class);
    private final QueryCredits credits = mock(QueryCredits.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new EngagementAdoptionController(adoption), new EngagementTrainingController(training),
            new EngagementCreditsController(credits))
        .setControllerAdvice(new EngagementApiExceptionHandler())
        .build();

    @BeforeEach
    void setContext() {
        TenantContextHolder.set(new TenantContext(tenantId, actor));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsAdoptionTotalsDepartmentAndCoverage() throws Exception {
        when(adoption.query(new AdoptionCommand(tenantId, actor, SourceKind.HISTORICO, AS_OF, departmentId)))
            .thenReturn(adoptionReport(SourceKind.HISTORICO));

        mvc.perform(request(ADOPTION).param("ate", AS_OF.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceKind").value("HISTORICO"))
            .andExpect(jsonPath("$.ate").value(AS_OF.toString()))
            .andExpect(jsonPath("$.janelaDias").value(30))
            .andExpect(jsonPath("$.elegiveis").value(9))
            .andExpect(jsonPath("$.ativos").value(5))
            .andExpect(jsonPath("$.inativos").value(4))
            .andExpect(jsonPath("$.taxaAdocao").value(55.56))
            .andExpect(jsonPath("$.licencasOciosas").value(2))
            .andExpect(jsonPath("$.dataStatus").value("PARTIAL"))
            .andExpect(jsonPath("$.porDepartamento[0].departmentId").value(departmentId.toString()))
            .andExpect(jsonPath("$.porDepartamento[0].elegiveis").value(4))
            .andExpect(jsonPath("$.porDepartamento[0].ativos").value(3))
            .andExpect(jsonPath("$.porDepartamento[0].taxaAdocao").value(75.0))
            .andExpect(jsonPath("$.cobertura.eligibleTotal").value(9))
            .andExpect(jsonPath("$.cobertura.eligibleLinked").value(8))
            .andExpect(jsonPath("$.cobertura.unmappedLicenseStatus").value(2))
            .andExpect(jsonPath("$.cobertura.unmappedTrainingStatus").value(3));
    }

    @Test
    void returnsTrainingQuadrantsAndNominalUsers() throws Exception {
        when(training.query(new AdoptionCommand(tenantId, actor, SourceKind.HISTORICO, AS_OF, departmentId)))
            .thenReturn(trainingReport(SourceKind.HISTORICO));

        mvc.perform(request(TRAINING).param("ate", AS_OF.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceKind").value("HISTORICO"))
            .andExpect(jsonPath("$.ate").value(AS_OF.toString()))
            .andExpect(jsonPath("$.listaNominal").value(true))
            .andExpect(jsonPath("$.quadrantes.TREINADO_ATIVO.total").value(1))
            .andExpect(jsonPath("$.quadrantes.TREINADO_ATIVO.usuarios[0].userId").value(member.toString()))
            .andExpect(jsonPath("$.quadrantes.TREINADO_ATIVO.usuarios[0].nome").value("Ana"))
            .andExpect(jsonPath("$.quadrantes.TREINADO_ATIVO.usuarios[0].departmentId")
                .value(departmentId.toString()))
            .andExpect(jsonPath("$.quadrantes.TREINADO_INATIVO.total").value(2))
            .andExpect(jsonPath("$.quadrantes.NAO_TREINADO_ATIVO.total").value(3))
            .andExpect(jsonPath("$.quadrantes.NAO_TREINADO_INATIVO.total").value(4))
            .andExpect(jsonPath("$.quadrantes.NAO_TREINADO_INATIVO.truncado").value(true))
            .andExpect(jsonPath("$.cobertura.eligibleLinked").value(8));
    }

    @Test
    void returnsCreditsComponentsAndPersonalRank() throws Exception {
        when(credits.query(new CreditsCommand(tenantId, actor, SourceKind.HISTORICO, MONTH, departmentId)))
            .thenReturn(creditsReport(SourceKind.HISTORICO, MONTH));

        mvc.perform(request(CREDITS).param("mes", " 2026-07 "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceKind").value("HISTORICO"))
            .andExpect(jsonPath("$.mes").value("2026-07"))
            .andExpect(jsonPath("$.status").value("FECHADO"))
            .andExpect(jsonPath("$.calculationVersion").value("credits-v1"))
            .andExpect(jsonPath("$.posicaoPessoal").value(3))
            .andExpect(jsonPath("$.itens[0].userId").value(member.toString()))
            .andExpect(jsonPath("$.itens[0].nome").value("Ana"))
            .andExpect(jsonPath("$.itens[0].departmentId").value(departmentId.toString()))
            .andExpect(jsonPath("$.itens[0].score").value(64.0))
            .andExpect(jsonPath("$.itens[0].usage").value(0.4))
            .andExpect(jsonPath("$.itens[0].training").value(1.0))
            .andExpect(jsonPath("$.itens[0].licenseApplicability").value("NOT_APPLICABLE"))
            .andExpect(jsonPath("$.itens[0].dataStatus").value("PARTIAL"))
            .andExpect(jsonPath("$.cobertura.eligibleLinked").value(8));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void usesOperationalSourceAndCurrentUtcMonthForOmittedOrEmptyParameters(String parameter) throws Exception {
        AdoptionCommand defaults = new AdoptionCommand(tenantId, actor, SourceKind.OPERACIONAL, null, null);
        when(adoption.query(defaults)).thenReturn(adoptionReport(SourceKind.OPERACIONAL));
        when(training.query(defaults)).thenReturn(trainingReport(SourceKind.OPERACIONAL));
        when(credits.query(any())).thenAnswer(invocation -> {
            CreditsCommand command = invocation.getArgument(0);
            return creditsReport(command.source(), command.month());
        });
        YearMonth before = YearMonth.now(ZoneOffset.UTC);

        mvc.perform(defaultRequest(ADOPTION, parameter)).andExpect(status().isOk());
        mvc.perform(defaultRequest(TRAINING, parameter)).andExpect(status().isOk());
        mvc.perform(defaultRequest(CREDITS, parameter)).andExpect(status().isOk());

        ArgumentCaptor<CreditsCommand> command = ArgumentCaptor.forClass(CreditsCommand.class);
        verify(credits).query(command.capture());
        YearMonth month = command.getValue().month();
        assertThat(month).isIn(before, YearMonth.now(ZoneOffset.UTC));
        assertThat(command.getValue())
            .isEqualTo(new CreditsCommand(tenantId, actor, SourceKind.OPERACIONAL, month, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {ADOPTION, TRAINING, CREDITS})
    void rejectsInvalidSourceBeforeCallingTheUseCase(String route) throws Exception {
        mvc.perform(get(BASE + route).param("fonte", "invalida"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("fonte inválida."));

        verifyNoInteractions(adoption, training, credits);
    }

    @Test
    void rejectsMalformedMonth() throws Exception {
        mvc.perform(get(BASE + CREDITS).param("mes", "2026-13"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("mes inválido."));

        verifyNoInteractions(credits);
    }

    @Test
    void rejectsMalformedDepartmentId() throws Exception {
        mvc.perform(get(BASE + ADOPTION).param("departmentId", "invalid-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(adoption);
    }

    @Test
    void returnsForbiddenWhenDepartmentIsOutsideTheActorsScope() throws Exception {
        when(adoption.query(new AdoptionCommand(tenantId, actor, SourceKind.HISTORICO, AS_OF, departmentId)))
            .thenThrow(new EngagementAccessDeniedException());

        mvc.perform(request(ADOPTION).param("ate", AS_OF.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USAGE_ACCESS_DENIED"))
            .andExpect(jsonPath("$.message").value("Acesso negado ao recorte de engajamento solicitado."));
    }

    @Test
    void returnsUnauthorizedWhenContextIsMissing() throws Exception {
        TenantContextHolder.clear();

        mvc.perform(get(BASE + ADOPTION))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.message").value("Não autenticado."));

        verifyNoInteractions(adoption);
    }

    @Test
    void returnsFallbackMessageForValidationErrorWithoutMessage() throws Exception {
        when(adoption.query(any())).thenThrow(new IllegalArgumentException());

        mvc.perform(get(BASE + ADOPTION))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("Dados inválidos."));
    }

    private MockHttpServletRequestBuilder request(String route) {
        return get(BASE + route).param("fonte", " historico ").param("departmentId", departmentId.toString());
    }

    private MockHttpServletRequestBuilder defaultRequest(String route, String parameter) {
        MockHttpServletRequestBuilder request = get(BASE + route);
        if (parameter != null) {
            request.param("fonte", parameter);
            if (CREDITS.equals(route)) {
                request.param("mes", parameter);
            }
        }
        return request;
    }

    private AdoptionReport adoptionReport(SourceKind source) {
        AdoptionResult overall = new AdoptionResult(9, 5, 4, new BigDecimal("55.56"), 2, DataStatus.PARTIAL);
        AdoptionResult department = new AdoptionResult(4, 3, 1, new BigDecimal("75.0"), 0, DataStatus.COMPLETE);
        return new AdoptionReport(source, AS_OF, 30, overall,
            List.of(new AdoptionReport.DepartmentAdoption(departmentId, department)), COVERAGE);
    }

    private TrainingCrossReport trainingReport(SourceKind source) {
        return new TrainingCrossReport(source, AS_OF,
            new TrainingCrossReport.QuadrantReport(1,
                List.of(new TrainingCrossReport.UserView(member, "Ana", departmentId)), false),
            new TrainingCrossReport.QuadrantReport(2, List.of(), false),
            new TrainingCrossReport.QuadrantReport(3, List.of(), false),
            new TrainingCrossReport.QuadrantReport(4, List.of(), true), true, COVERAGE);
    }

    private CreditsReport creditsReport(SourceKind source, YearMonth month) {
        return new CreditsReport(source, month, "FECHADO", "credits-v1", List.of(
            new CreditsReport.Item(member, "Ana", departmentId, new BigDecimal("64.0"),
                new BigDecimal("0.4"), BigDecimal.ONE, null, "NOT_APPLICABLE", "PARTIAL")), COVERAGE, 3);
    }
}
