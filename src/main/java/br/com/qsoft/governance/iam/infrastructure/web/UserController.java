package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.iam.application.AssignRole;
import br.com.qsoft.governance.iam.application.AssignRoleCommand;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevelCommand;
import br.com.qsoft.governance.iam.application.ChangeUserStatus;
import br.com.qsoft.governance.iam.application.ChangeUserStatusCommand;
import br.com.qsoft.governance.iam.application.GetUserAccess;
import br.com.qsoft.governance.iam.application.ListUsers;
import br.com.qsoft.governance.iam.application.ListUsersCommand;
import br.com.qsoft.governance.iam.application.RevokeRoleAssignment;
import br.com.qsoft.governance.iam.application.RevokeRoleAssignmentCommand;
import br.com.qsoft.governance.iam.application.UpdateUser;
import br.com.qsoft.governance.iam.application.UpdateUserCommand;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.GrantOrigin;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * <p>
 * O tenant vem do contexto autenticado (nunca do corpo) e as permissões são
 * verificadas pelo {@link PermissionInterceptor}. Os endpoints com alvo usam
 * {@code BY_TARGET} e aplicam a checagem fina no caso de uso ({@code domina},
 * {@code visibilityFor}, {@code isAllowed}) — SPEC-01 §4/§6.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UserController {

    private final ChangeUserHierarchyLevel changeUserHierarchyLevel;
    private final ChangeUserStatus changeUserStatus;
    private final ListUsers listUsers;
    private final UpdateUser updateUser;
    private final PolicyEvaluator policyEvaluator;
    private final AssignRole assignRole;
    private final RevokeRoleAssignment revokeRoleAssignment;
    private final UserRepository userRepository;
    private final GetUserAccess getUserAccess;

    public UserController(ChangeUserHierarchyLevel changeUserHierarchyLevel,
                          ChangeUserStatus changeUserStatus,
                          ListUsers listUsers,
                          UpdateUser updateUser,
                          PolicyEvaluator policyEvaluator,
                          AssignRole assignRole,
                          RevokeRoleAssignment revokeRoleAssignment,
                          UserRepository userRepository,
                          GetUserAccess getUserAccess) {
        this.changeUserHierarchyLevel = changeUserHierarchyLevel;
        this.changeUserStatus = changeUserStatus;
        this.listUsers = listUsers;
        this.updateUser = updateUser;
        this.policyEvaluator = policyEvaluator;
        this.assignRole = assignRole;
        this.revokeRoleAssignment = revokeRoleAssignment;
        this.userRepository = userRepository;
        this.getUserAccess = getUserAccess;
    }

    /**
     * Lista/busca usuários do tenant, paginado e com filtros combináveis
     * (SPI-62), restrito por {@code visibilityFor(USER:READ)} (SPEC-01 §6).
     */
    @GetMapping
    @RequiresPermission(resource = Resource.USER, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<UserPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) HierarchyLevel hierarchyLevel,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email) {
        TenantContext context = TenantContextHolder.require();
        PageResult<User> result = listUsers.list(new ListUsersCommand(
            context.tenantId(), context.userId(), hierarchyLevel, departmentId,
            name, email, page, size));
        return ResponseEntity.ok(UserPageResponse.from(result));
    }

    /** Edita o Department de um usuário. Requer USER:MANAGE (BY_TARGET + domina). */
    @PatchMapping("/{id}")
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        TenantContext context = TenantContextHolder.require();
        User user = updateUser.update(new UpdateUserCommand(
            context.userId(), UserId.of(id), request.departmentId()));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Desativa ou reativa um usuário (SPI-61). Requer USER:MANAGE
     * (BY_TARGET + domina; último admin → 409).
     */
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<UserResponse> changeStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody UserStatusRequest request) {
        TenantContext context = TenantContextHolder.require();
        User user = changeUserStatus.change(new ChangeUserStatusCommand(
            context.userId(), UserId.of(id), request.status()));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Altera o rótulo hierárquico de um usuário (sem efeito de autorização).
     * Requer USER:MANAGE (BY_TARGET + domina).
     */
    @PostMapping("/{id}/nivel")
    @RequiresPermission(resource = Resource.USER, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
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

    /** Concessões e permissões efetivas do próprio usuário (SPEC-01 §7.3). */
    @GetMapping("/me/permissoes")
    public ResponseEntity<UserAccessResponse> myPermissions() {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(UserAccessResponse.from(
            getUserAccess.forUser(context.tenantId(), context.userId())));
    }

    /**
     * Permissões efetivas de um usuário (SPI-44/SPEC §7.3). Requer USER:READ e
     * {@code isAllowed(OwnedTarget(alvo, depto))}; alvo de outro tenant → 404.
     */
    @GetMapping("/{id}/permissoes")
    @RequiresPermission(resource = Resource.USER, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<UserAccessResponse> userPermissions(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        UserId targetId = UserId.of(id);
        User target = userRepository.findByTenantIdAndId(context.tenantId(), targetId)
            .orElseThrow(() -> new UserNotFoundException(targetId));

        boolean allowed = policyEvaluator.isAllowed(context.tenantId(), context.userId(),
            Resource.USER, Action.READ,
            new AccessTarget.OwnedTarget(target.id(), target.departmentId()));
        if (!allowed) {
            throw new AuthorizationDeniedException(
                "Sem acesso às permissões deste usuário.");
        }

        return ResponseEntity.ok(UserAccessResponse.from(
            getUserAccess.forUser(context.tenantId(), targetId)));
    }

    /**
     * Atribui uma concessão de papel a um usuário existente (SPI-47).
     * Requer {@code ROLE:MANAGE} (só {@code TENANT_ADMIN}); a origem é DIRECT.
     */
    @PostMapping("/{id}/concessoes")
    @RequiresPermission(resource = Resource.ROLE, action = Action.MANAGE)
    public ResponseEntity<AssignmentResponse> assign(@PathVariable UUID id,
                                                     @Valid @RequestBody AssignRoleRequest request) {
        TenantContext context = TenantContextHolder.require();
        RoleAssignment assignment = assignRole.assign(new AssignRoleCommand(
            context.tenantId(), context.userId(), UserId.of(id), request.roleKey(),
            request.departmentId(), GrantOrigin.DIRECT));
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentResponse.from(assignment));
    }

    /**
     * Revoga (logicamente) uma concessão de um usuário (SPI-47).
     * Requer {@code ROLE:MANAGE}; o último {@code TENANT_ADMIN} ativo → 409.
     */
    @DeleteMapping("/{id}/concessoes/{concessaoId}")
    @RequiresPermission(resource = Resource.ROLE, action = Action.MANAGE)
    public ResponseEntity<Void> revokeAssignment(@PathVariable UUID id,
                                                 @PathVariable UUID concessaoId) {
        TenantContext context = TenantContextHolder.require();
        revokeRoleAssignment.revoke(new RevokeRoleAssignmentCommand(
            context.tenantId(), context.userId(), UserId.of(id),
            RoleAssignmentId.of(concessaoId)));
        return ResponseEntity.noContent().build();
    }
}
