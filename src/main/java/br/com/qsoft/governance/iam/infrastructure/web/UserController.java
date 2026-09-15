package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de criação de usuário.
 * Requer JWT (endpoint protegido): o tenant é derivado do contexto autenticado
 * (nunca do corpo) e é exigida a permissão MANAGE_USERS.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UserController {

    private final ProvisionUser provisionUser;
    private final AuthorizationQuery authorizationQuery;

    public UserController(ProvisionUser provisionUser, AuthorizationQuery authorizationQuery) {
        this.provisionUser = provisionUser;
        this.authorizationQuery = authorizationQuery;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        TenantContext context = TenantContextHolder.require();

        AuthorizationResult authorization =
            authorizationQuery.checkPermission(context.userId(), Permission.MANAGE_USERS);
        if (!authorization.allowed()) {
            throw new AuthorizationDeniedException(
                authorization.denialReason() != null ? authorization.denialReason()
                    : "Usuário não autorizado a criar usuários.");
        }

        User user = provisionUser.provision(new ProvisionUserCommand(
            context.tenantId(),
            request.email(),
            request.name(),
            request.hierarchyLevel(),
            request.password(),
            request.departmentId(),
            Set.of()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }
}
