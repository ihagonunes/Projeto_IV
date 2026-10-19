package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.ScopeType;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de emissão de convite (ADR-37): resolve o papel de sistema do
 * tenant, valida a delegação ({@link GrantAuthority}), gera um token aleatório
 * (SecureRandom ≥ 128 bits) e persiste apenas o SHA-256 em hex. O token puro é
 * devolvido uma única vez e nunca vai para log.
 */
@Service
public class CreateInvite {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InviteRepository inviteRepository;
    private final GrantAuthority grantAuthority;
    private final long ttlHours;

    public CreateInvite(TenantRepository tenantRepository,
                        UserRepository userRepository,
                        RoleRepository roleRepository,
                        InviteRepository inviteRepository,
                        GrantAuthority grantAuthority,
                        @Value("${app.security.invite.ttl-hours:48}") long ttlHours) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.inviteRepository = inviteRepository;
        this.grantAuthority = grantAuthority;
        this.ttlHours = ttlHours;
    }

    public IssuedInvite create(CreateInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String email = requireNonBlank(command.email(), "email");
        String roleKey = requireNonBlank(command.roleKey(), "roleKey");
        UUID departmentId = command.departmentId();
        UUID invitedBy = command.invitedBy();

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new TenantNotFoundException(tenantId);
        }

        if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        SystemRole targetRole = SystemRole.fromKey(roleKey)
            .orElseThrow(() -> new IllegalArgumentException("roleKey inválido: " + roleKey + "."));
        if (targetRole.scope() == ScopeType.DEPARTMENT && departmentId == null) {
            throw new IllegalArgumentException(
                "departmentId é obrigatório para papel departamental.");
        }
        if (invitedBy == null
            || !grantAuthority.canGrantByInvite(tenantId, UserId.of(invitedBy), targetRole, departmentId)) {
            throw new AuthorizationDeniedException(
                "Ator não pode conceder o papel " + roleKey + ".");
        }

        Role role = roleRepository.findByTenantIdAndName(tenantId, roleKey)
            .orElseThrow(() -> new IllegalStateException("Papel de sistema ausente: " + roleKey + "."));

        HierarchyLevel level = resolveLevel(command.hierarchyLevel(), targetRole);
        String rawToken = InviteTokens.random();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        Invite invite = Invite.create(
            InviteId.random(), tenantId, email, level, departmentId,
            role.id().value(), invitedBy, InviteTokenHasher.sha256Hex(rawToken), expiresAt);
        return new IssuedInvite(inviteRepository.save(invite), rawToken);
    }

    private static HierarchyLevel resolveLevel(HierarchyLevel provided, SystemRole role) {
        if (provided != null) {
            return provided;
        }
        return switch (role) {
            case COLLABORATOR -> HierarchyLevel.COLLABORATOR;
            case LEADER -> HierarchyLevel.LEADER;
            case MANAGER -> HierarchyLevel.MANAGER;
            case EXECUTIVE, TENANT_ADMIN -> HierarchyLevel.EXECUTIVE;
        };
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
