package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevelCommand;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.rbac.EvaluationContext;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.rbac.Resource;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de gestão de usuários (protegidos por JWT).
 * O tenant vem do contexto autenticado (nunca do corpo) e as permissões são
 * verificadas pelo {@link PermissionInterceptor} (via {@link RequiresPermission}).
 */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UserController {

    private final ProvisionUser provisionUser;
    private final ChangeUserHierarchyLevel changeUserHierarchyLevel;
    private final PolicyEvaluator policyEvaluator;

    public UserController(ProvisionUser provisionUser,
                          ChangeUserHierarchyLevel changeUserHierarchyLevel,
                          PolicyEvaluator policyEvaluator) {
        this.provisionUser = provisionUser;
        this.changeUserHierarchyLevel = changeUserHierarchyLevel;
        this.policyEvaluator = policyEvaluator;
    }

    /** Cria usuário no tenant do contexto autenticado. Requer permissão USER:MANAGE. */
    @PostMapping
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        TenantContext context = TenantContextHolder.require();

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

    /** Altera o nível hierárquico de um usuário. Requer permissão USER:MANAGE (SPI-47). */
    @PostMapping("/{id}/nivel")
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE)
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

    /** Permissões efetivas do próprio usuário autenticado (SPI-44). */
    @GetMapping("/me/permissoes")
    public ResponseEntity<EffectivePermissionsResponse> myPermissions() {
        TenantContext context = TenantContextHolder.require();
        Set<Permission> permissions = policyEvaluator.effectivePermissions(
            EvaluationContext.of(context.tenantId(), context.userId()));
        return ResponseEntity.ok(EffectivePermissionsResponse.from(context.userId().value(), permissions));
    }

    /** Permissões efetivas de um usuário. Requer permissão USER:READ (SPI-44). */
    @GetMapping("/{id}/permissoes")
    @RequiresPermission(resource = Resource.USER, action = Action.READ)
    public ResponseEntity<EffectivePermissionsResponse> userPermissions(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        Set<Permission> permissions = policyEvaluator.effectivePermissions(
            new EvaluationContext(context.tenantId(), UserId.of(id), null));
        return ResponseEntity.ok(EffectivePermissionsResponse.from(id, permissions));
    }
}
