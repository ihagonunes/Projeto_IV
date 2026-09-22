package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
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
 * Caso de uso de emissão de convite (SPI-29): gera um token aleatório e único,
 * com expiração configurada, dentro do tenant.
 */
@Service
public class CreateInvite {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final long ttlHours;

    public CreateInvite(TenantRepository tenantRepository,
                        UserRepository userRepository,
                        InviteRepository inviteRepository,
                        @Value("${app.security.invite.ttl-hours:48}") long ttlHours) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.ttlHours = ttlHours;
    }

    public Invite create(CreateInviteCommand command) {
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

        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        Invite invite = Invite.create(
            InviteId.random(), tenantId, email, level, command.departmentId(),
            generateToken(), expiresAt);
        return inviteRepository.save(invite);
    }

    private String generateToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (inviteRepository.existsByToken(token));
        return token;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
