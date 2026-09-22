package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.application.rbac.EvaluationContext;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptador HTTP que executa o {@link PolicyEvaluator} nas rotas anotadas
 * com {@link RequiresPermission}. Sem contexto autenticado → 401; sem permissão
 * → 403.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PolicyEvaluator policyEvaluator;

    public PermissionInterceptor(PolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPermission annotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return true;
        }

        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado.");
            return false;
        }

        Permission permission = Permission.of(annotation.resource(), annotation.action());
        AuthorizationResult result = policyEvaluator.evaluate(
            EvaluationContext.of(context.tenantId(), context.userId()), permission);

        if (!result.allowed()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                result.denialReason() != null ? result.denialReason() : "Acesso negado.");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}
