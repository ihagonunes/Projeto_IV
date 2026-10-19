package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de emissão de convite (SPI-29): gera um token aleatório, guarda
 * apenas o seu hash SHA-256 e devolve o token puro uma única vez (ADR-37).
 * <p>
 * Provisoriamente (a regra definitiva é a TASK-S3-A5), o papel da concessão é o
 * papel de sistema cujo nome é igual ao {@code hierarchyLevel} pedido.
 */
@Service
public class CreateInvite {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InviteRepository inviteRepository;
    private final long ttlHours;

    public CreateInvite(TenantRepository tenantRepository,
                        UserRepository userRepository,
                        RoleRepository roleRepository,
                        InviteRepository inviteRepository,
                        @Value("${app.security.invite.ttl-hours:48}") long ttlHours) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.inviteRepository = inviteRepository;
        this.ttlHours = ttlHours;
    }

    public IssuedInvite create(CreateInviteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String email = requireNonBlank(command.email(), "email");
        HierarchyLevel level = Objects.requireNonNull(command.hierarchyLevel(), "hierarchyLevel");

        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new TenantNotFoundException(tenantId);
        }

        if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        Role role = roleRepository.findByTenantIdAndName(tenantId, level.name())
            .orElseThrow(() -> new IllegalStateException(
                "Papel de sistema ausente para o nível " + level.name() + "."));

        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        Invite invite = Invite.create(
            InviteId.random(), tenantId, email, level, command.departmentId(),
            role.id().value(), command.invitedBy(), InviteTokenHasher.sha256Hex(rawToken), expiresAt);
        return new IssuedInvite(inviteRepository.save(invite), rawToken);
    }

    private String generateToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (inviteRepository.existsByTokenHash(InviteTokenHasher.sha256Hex(token)));
        return token;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
