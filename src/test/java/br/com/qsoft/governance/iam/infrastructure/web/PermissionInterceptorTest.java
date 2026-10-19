package br.com.qsoft.governance.iam.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;

/**
 * Testes unitários do {@link PermissionInterceptor}: handler não-HandlerMethod,
 * sem @RequiresPermission, sem TenantContext (401), gate TENANT_ONLY e
 * BY_TARGET (403/pass-through).
 */
@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    @Mock
    private PolicyEvaluator policyEvaluator;

    private PermissionInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor(policyEvaluator, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private HandlerMethod annotated(Targeting targeting) {
        HandlerMethod handler = mock(HandlerMethod.class);
        RequiresPermission annotation = mock(RequiresPermission.class);
        when(annotation.resource()).thenReturn(Resource.USER);
        when(annotation.action()).thenReturn(Action.MANAGE);
        when(annotation.targeting()).thenReturn(targeting);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(annotation);
        return handler;
    }

    private void withContext() {
        TenantContextHolder.set(new TenantContext(TenantId.random(), UserId.random()));
    }

    @Test
    void deve_retornarTrue_quando_handlerNaoEHandlerMethod() throws Exception {
        boolean result = interceptor.preHandle(request, response, "not-a-handler-method");

        assertThat(result).isTrue();
        verify(policyEvaluator, never()).gateTenantOnly(any(), any(), any(), any());
    }

    @Test
    void deve_retornarTrue_quando_metodoSemAnotacaoRequiresPermission() throws Exception {
        HandlerMethod handler = mock(HandlerMethod.class);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        verify(policyEvaluator, never()).gateTenantOnly(any(), any(), any(), any());
    }

    @Test
    void deve_retornarFalse_e_escrever401_quando_semContextoAutenticado() throws Exception {
        HandlerMethod handler = mock(HandlerMethod.class);
        when(handler.getMethodAnnotation(RequiresPermission.class))
            .thenReturn(mock(RequiresPermission.class));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(sw.toString()).contains("Não autenticado");
        verify(policyEvaluator, never()).gateTenantOnly(any(), any(), any(), any());
    }

    @Test
    void deve_retornarFalse_e_escrever403_quando_tenantOnlySemEscopoTenant() throws Exception {
        withContext();
        when(policyEvaluator.gateTenantOnly(any(), any(), any(), any())).thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, annotated(Targeting.TENANT_ONLY));

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(sw.toString()).contains("USER:MANAGE");
    }

    @Test
    void deve_retornarTrue_quando_tenantOnlyComEscopoTenant() throws Exception {
        withContext();
        when(policyEvaluator.gateTenantOnly(any(), any(), any(), any())).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, annotated(Targeting.TENANT_ONLY));

        assertThat(result).isTrue();
        verify(response, never()).setStatus(any(int.class));
        verify(policyEvaluator, never()).gateByTarget(any(), any(), any(), any());
    }

    @Test
    void deve_usarGateByTarget_quando_annotacaoByTarget() throws Exception {
        withContext();
        when(policyEvaluator.gateByTarget(any(), any(), any(), any())).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, annotated(Targeting.BY_TARGET));

        assertThat(result).isTrue();
        verify(policyEvaluator).gateByTarget(any(), any(), any(), any());
        verify(policyEvaluator, never()).gateTenantOnly(any(), any(), any(), any());
    }
}
