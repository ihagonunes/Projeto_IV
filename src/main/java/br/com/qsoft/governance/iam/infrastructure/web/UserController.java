package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevelCommand;
import br.com.qsoft.governance.iam.application.ChangeUserStatus;
import br.com.qsoft.governance.iam.application.ChangeUserStatusCommand;
import br.com.qsoft.governance.iam.application.ListUsers;
import br.com.qsoft.governance.iam.application.ListUsersCommand;
import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.UpdateUser;
import br.com.qsoft.governance.iam.application.UpdateUserCommand;
import br.com.qsoft.governance.iam.application.rbac.EvaluationContext;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ChangeUserStatus changeUserStatus;
    private final ListUsers listUsers;
    private final UpdateUser updateUser;
    private final PolicyEvaluator policyEvaluator;

    public UserController(ProvisionUser provisionUser,
                          ChangeUserHierarchyLevel changeUserHierarchyLevel,
                          ChangeUserStatus changeUserStatus,
                          ListUsers listUsers,
                          UpdateUser updateUser,
                          PolicyEvaluator policyEvaluator) {
        this.provisionUser = provisionUser;
        this.changeUserHierarchyLevel = changeUserHierarchyLevel;
        this.changeUserStatus = changeUserStatus;
        this.listUsers = listUsers;
        this.updateUser = updateUser;
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

    /**
     * Lista/busca usuários do tenant do contexto autenticado, de forma paginada e
     * com filtros combináveis (SPI-62). Requer permissão USER:READ.
     */
    @GetMapping
    @RequiresPermission(resource = Resource.USER, action = Action.READ)
    public ResponseEntity<UserPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) HierarchyLevel hierarchyLevel,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email) {
        TenantContext context = TenantContextHolder.require();
        PageResult<User> result = listUsers.list(new ListUsersCommand(
            context.tenantId(), hierarchyLevel, departmentId, name, email, page, size));
        return ResponseEntity.ok(UserPageResponse.from(result));
    }

    /** Edita papel (HierarchyLevel) e Department de um usuário. Requer USER:MANAGE (SPI-63). */
    @PatchMapping("/{id}")
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE)
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        TenantContext context = TenantContextHolder.require();
        User user = updateUser.update(new UpdateUserCommand(
            context.userId(), UserId.of(id), request.hierarchyLevel(), request.departmentId()));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Desativa ou reativa um usuário (SPI-61). A desativação é lógica: o
     * histórico é preservado e o usuário inativo não autentica. Requer USER:MANAGE.
     */
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE)
    public ResponseEntity<UserResponse> changeStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody UserStatusRequest request) {
        TenantContext context = TenantContextHolder.require();
        User user = changeUserStatus.change(new ChangeUserStatusCommand(
            context.userId(), UserId.of(id), request.status()));
        return ResponseEntity.ok(UserResponse.from(user));
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
