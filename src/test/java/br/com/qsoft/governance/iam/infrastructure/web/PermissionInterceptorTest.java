package br.com.qsoft.governance.iam.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.application.rbac.EvaluationContext;
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
 * Testes unitários do {@link PermissionInterceptor}.
 * Exercita os branches: handler não-HandlerMethod, sem @RequiresPermission,
 * sem TenantContext (401), sem permissão (403), e com permissão (pass-through).
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

    // ── handler não é HandlerMethod → deixa passar ────────────────────────────

    @Test
    void deve_retornarTrue_quando_handlerNaoEHandlerMethod() throws Exception {
        boolean result = interceptor.preHandle(request, response, "not-a-handler-method");

        assertThat(result).isTrue();
        verify(policyEvaluator, never()).evaluate(any(), any());
    }

    // ── handler sem @RequiresPermission → deixa passar ────────────────────────

    @Test
    void deve_retornarTrue_quando_metodoSemAnotacaoRequiresPermission() throws Exception {
        HandlerMethod handler = mock(HandlerMethod.class);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        verify(policyEvaluator, never()).evaluate(any(), any());
    }

    // ── sem TenantContext → 401 ───────────────────────────────────────────────

    @Test
    void deve_retornarFalse_e_escrever401_quando_semContextoAutenticado() throws Exception {
        HandlerMethod handler = mock(HandlerMethod.class);
        RequiresPermission annotation = mock(RequiresPermission.class);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(annotation);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(sw.toString()).contains("Não autenticado");
        verify(policyEvaluator, never()).evaluate(any(), any());
    }

    // ── com contexto mas sem permissão → 403 ─────────────────────────────────

    @Test
    void deve_retornarFalse_e_escrever403_quando_semPermissao() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, userId));

        HandlerMethod handler = mock(HandlerMethod.class);
        RequiresPermission annotation = mock(RequiresPermission.class);
        when(annotation.resource()).thenReturn(Resource.USER);
        when(annotation.action()).thenReturn(Action.MANAGE);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(annotation);

        when(policyEvaluator.evaluate(any(EvaluationContext.class), any()))
            .thenReturn(AuthorizationResult.deny("Sem permissão."));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(sw.toString()).contains("Sem permissão");
    }

    // ── com contexto e com permissão → pass-through ──────────────────────────

    @Test
    void deve_retornarTrue_quando_comPermissao() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, userId));

        HandlerMethod handler = mock(HandlerMethod.class);
        RequiresPermission annotation = mock(RequiresPermission.class);
        when(annotation.resource()).thenReturn(Resource.USER);
        when(annotation.action()).thenReturn(Action.MANAGE);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(annotation);

        when(policyEvaluator.evaluate(any(EvaluationContext.class), any()))
            .thenReturn(AuthorizationResult.allow());

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        verify(response, never()).setStatus(any(int.class));
    }

    // ── 403 com denialReason nulo → mensagem padrão ──────────────────────────

    @Test
    void deve_usarMensagemPadrao_quando_denialReasonNulo() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, userId));

        HandlerMethod handler = mock(HandlerMethod.class);
        RequiresPermission annotation = mock(RequiresPermission.class);
        when(annotation.resource()).thenReturn(Resource.USER);
        when(annotation.action()).thenReturn(Action.READ);
        when(handler.getMethodAnnotation(RequiresPermission.class)).thenReturn(annotation);

        // denied com reason null
        when(policyEvaluator.evaluate(any(EvaluationContext.class), any()))
            .thenReturn(AuthorizationResult.deny(null));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(sw.toString()).contains("Acesso negado");
    }
}
