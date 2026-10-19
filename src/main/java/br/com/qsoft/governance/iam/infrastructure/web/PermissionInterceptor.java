package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Interceptador HTTP que executa o {@link PolicyEvaluator} nas rotas anotadas
 * com {@link RequiresPermission}. Sem contexto autenticado → 401; sem permissão
 * → 403. O gate é fechado por padrão ({@link Targeting#TENANT_ONLY}); só
 * endpoints com checagem fina no caso de uso declaram {@link Targeting#BY_TARGET}.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PolicyEvaluator policyEvaluator;
    private final ObjectMapper objectMapper;

    public PermissionInterceptor(PolicyEvaluator policyEvaluator, ObjectMapper objectMapper) {
        this.policyEvaluator = policyEvaluator;
        this.objectMapper = objectMapper;
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

        boolean allowed = annotation.targeting() == Targeting.BY_TARGET
            ? policyEvaluator.gateByTarget(
                context.tenantId(), context.userId(), annotation.resource(), annotation.action())
            : policyEvaluator.gateTenantOnly(
                context.tenantId(), context.userId(), annotation.resource(), annotation.action());

        if (!allowed) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                "Permissão negada: " + annotation.resource() + ":" + annotation.action() + ".");
            return false;
        }
        return true;
    }

    /** Serializa o erro com Jackson — evita JSON inválido por concatenação de string. */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }
}
