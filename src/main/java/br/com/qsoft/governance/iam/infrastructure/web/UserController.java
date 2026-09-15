package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevelCommand;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de gestão de usuários (protegidos por JWT).
 * O tenant vem do contexto autenticado (nunca do corpo) e as permissões são
 * verificadas via AuthorizationQuery.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UserController {

    private final ProvisionUser provisionUser;
    private final ChangeUserHierarchyLevel changeUserHierarchyLevel;
    private final AuthorizationQuery authorizationQuery;

    public UserController(ProvisionUser provisionUser,
                          ChangeUserHierarchyLevel changeUserHierarchyLevel,
                          AuthorizationQuery authorizationQuery) {
        this.provisionUser = provisionUser;
        this.changeUserHierarchyLevel = changeUserHierarchyLevel;
        this.authorizationQuery = authorizationQuery;
    }

    /** Cria usuário no tenant do contexto autenticado. Requer MANAGE_USERS. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "criar usuários");

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

    /** Altera o nível hierárquico de um usuário. Requer MANAGE_HIERARCHY (no caso de uso). */
    @PostMapping("/{id}/nivel")
    public ResponseEntity<UserResponse> changeHierarchyLevel(@PathVariable UUID id,
                                                             @Valid @RequestBody HierarchyLevelRequest request) {
        TenantContext context = TenantContextHolder.require();
        User user = changeUserHierarchyLevel.change(new ChangeUserHierarchyLevelCommand(
            context.userId(),
            UserId.of(id),
            request.hierarchyLevel()
        ));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private void requirePermission(TenantContext context, Permission permission, String action) {
        AuthorizationResult authorization = authorizationQuery.checkPermission(context.userId(), permission);
        if (!authorization.allowed()) {
            throw new AuthorizationDeniedException(
                authorization.denialReason() != null ? authorization.denialReason()
                    : "Usuário não autorizado a " + action + ".");
        }
    }
}
