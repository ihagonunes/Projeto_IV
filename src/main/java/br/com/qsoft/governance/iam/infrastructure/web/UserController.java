package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevelCommand;
import br.com.qsoft.governance.iam.application.ChangeUserStatus;
import br.com.qsoft.governance.iam.application.ChangeUserStatusCommand;
import br.com.qsoft.governance.iam.application.CreateUser;
import br.com.qsoft.governance.iam.application.CreateUserCommand;
import br.com.qsoft.governance.iam.application.ListUsers;
import br.com.qsoft.governance.iam.application.ListUsersQuery;
import br.com.qsoft.governance.iam.application.ResendUserInvite;
import br.com.qsoft.governance.iam.application.ResendUserInviteCommand;
import br.com.qsoft.governance.iam.application.UpdateUserProfile;
import br.com.qsoft.governance.iam.application.UpdateUserProfileCommand;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.List;
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
 * verificadas via AuthorizationQuery (ADR-03 / AGENTS.md).
 */
@RestController
@RequestMapping({"/api/v1/usuarios", "/api/v1/users"})
public class UserController {

    private final CreateUser createUser;
    private final ChangeUserHierarchyLevel changeUserHierarchyLevel;
    private final ListUsers listUsers;
    private final UpdateUserProfile updateUserProfile;
    private final ChangeUserStatus changeUserStatus;
    private final ResendUserInvite resendUserInvite;
    private final AuthorizationQuery authorizationQuery;

    public UserController(CreateUser createUser,
                          ChangeUserHierarchyLevel changeUserHierarchyLevel,
                          ListUsers listUsers,
                          UpdateUserProfile updateUserProfile,
                          ChangeUserStatus changeUserStatus,
                          ResendUserInvite resendUserInvite,
                          AuthorizationQuery authorizationQuery) {
        this.createUser = createUser;
        this.changeUserHierarchyLevel = changeUserHierarchyLevel;
        this.listUsers = listUsers;
        this.updateUserProfile = updateUserProfile;
        this.changeUserStatus = changeUserStatus;
        this.resendUserInvite = resendUserInvite;
        this.authorizationQuery = authorizationQuery;
    }

    /** Lista usuários do tenant autenticado (SPI-62). Requer MANAGE_USERS. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@RequestParam(required = false) String search,
                                                   @RequestParam(required = false) String status) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "listar usuários");

        List<User> users = listUsers.list(new ListUsersQuery(context.tenantId(), search, status));
        List<UserResponse> response = users.stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    /** Cria usuário no tenant do contexto autenticado. Requer MANAGE_USERS. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "criar usuários");

        HierarchyLevel level = request.hierarchyLevel() != null ? request.hierarchyLevel() : HierarchyLevel.COLLABORATOR;

        User user = createUser.create(new CreateUserCommand(
            context.tenantId(),
            request.email(),
            request.name(),
            level,
            request.departmentId()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /** Atualiza departamento, nível hierárquico e papel administrativo do usuário (SPI-63). Requer MANAGE_USERS. */
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateProfile(@PathVariable UUID id,
                                                      @RequestBody UpdateUserProfileRequest request) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "atualizar perfil do usuário");

        User updatedUser = updateUserProfile.updateProfile(new UpdateUserProfileCommand(
            context.tenantId(),
            UserId.of(id),
            request.departmentId(),
            request.hierarchyLevel(),
            request.isAdmin()
        ));
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    /** Desativa o acesso de um usuário (SPI-61). Requer MANAGE_USERS. */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivate(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "desativar usuário");

        User updatedUser = changeUserStatus.changeStatus(new ChangeUserStatusCommand(
            context.tenantId(),
            context.userId(),
            UserId.of(id),
            UserStatus.INACTIVE
        ));
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    /** Reativa o acesso de um usuário (SPI-61). Requer MANAGE_USERS. */
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<UserResponse> reactivate(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "reativar usuário");

        User updatedUser = changeUserStatus.changeStatus(new ChangeUserStatusCommand(
            context.tenantId(),
            context.userId(),
            UserId.of(id),
            UserStatus.ACTIVE
        ));
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    /** Reenvia convite expirado/pendente para um usuário (SPI-59). Requer MANAGE_USERS. */
    @PostMapping({"/invites/{inviteId}/resend", "/{inviteId}/resend-invite"})
    public ResponseEntity<UserResponse> resendInvite(@PathVariable String inviteId) {
        TenantContext context = TenantContextHolder.require();
        requirePermission(context, Permission.MANAGE_USERS, "reenviar convite");

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(inviteId.replace("inv_", "").replace("usr_", ""));
        } catch (IllegalArgumentException ex) {
            targetUuid = UUID.nameUUIDFromBytes(inviteId.getBytes());
        }

        User updatedUser = resendUserInvite.resend(new ResendUserInviteCommand(
            context.tenantId(),
            UserId.of(targetUuid)
        ));
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    /** Altera o nível hierárquico de um usuário (compatibilidade retroativa). */
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
