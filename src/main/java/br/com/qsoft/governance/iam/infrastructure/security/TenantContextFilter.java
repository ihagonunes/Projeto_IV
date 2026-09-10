package br.com.qsoft.governance.iam.infrastructure.security;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Após a validação criptográfica do JWT (resource server), este filtro:
 * - revalida o estado atual de Tenant e User (ADR-30: desativação bloqueia
 *   mesmo com token ainda válido);
 * - garante que o token pertence ao mesmo tenant do usuário (ADR-03);
 * - propaga o TenantContext a partir do contexto autenticado,
 *   nunca de valores enviados pelo cliente.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public TenantContextFilter(TenantRepository tenantRepository, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                Jwt jwt = (Jwt) jwtAuthentication.getPrincipal();
                try {
                    UserId userId = UserId.fromString(jwt.getSubject());
                    TenantId tenantId = TenantId.fromString(jwt.getClaimAsString("tenant_id"));
                    if (!isActive(userId, tenantId)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                            "Acesso negado: tenant ou usuário inativo.");
                        return;
                    }
                    TenantContextHolder.set(new TenantContext(tenantId, userId));
                } catch (RuntimeException ex) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido.");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private boolean isActive(UserId userId, TenantId tenantId) {
        var tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || tenant.status() != TenantStatus.ACTIVE) {
            return false;
        }
        var user = userRepository.findById(userId).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            return false;
        }
        // Mesmo tenant: o token nunca permite acesso a outro tenant.
        return user.tenantId().equals(tenantId);
    }
}
