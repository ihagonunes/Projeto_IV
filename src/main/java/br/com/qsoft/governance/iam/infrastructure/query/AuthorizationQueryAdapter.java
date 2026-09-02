package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público AuthorizationQuery do IAM.
 * Autorização determinística: Tenant ACTIVE + User ACTIVE + permissão concedida.
 * "ADMIN" corresponde ao conjunto completo de permissões (ADR-04).
 */
@Component
public class AuthorizationQueryAdapter implements AuthorizationQuery {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public AuthorizationQueryAdapter(UserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public AuthorizationResult checkPermission(UserId userId, Permission permission) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return AuthorizationResult.deny("Usuário não encontrado.");
        }
        if (user.status() != UserStatus.ACTIVE) {
            return AuthorizationResult.deny("Usuário inativo.");
        }
        var tenant = tenantRepository.findById(user.tenantId()).orElse(null);
        if (tenant == null || tenant.status() != TenantStatus.ACTIVE) {
            return AuthorizationResult.deny("Tenant inativo.");
        }
        if (!user.hasPermission(permission)) {
            return AuthorizationResult.deny("Permissão negada: " + permission.name() + ".");
        }
        return AuthorizationResult.allow();
    }

    @Override
    public AuthorizationScope resolveScope(UserId userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return AuthorizationScope.SELF;
        }
        if (user.isAdmin()) {
            return AuthorizationScope.TENANT;
        }
        return switch (user.hierarchyLevel()) {
            case EXECUTIVE -> AuthorizationScope.TENANT;
            case MANAGER, LEADER -> AuthorizationScope.DEPARTMENT;
            case COLLABORATOR -> AuthorizationScope.SELF;
        };
    }
}
