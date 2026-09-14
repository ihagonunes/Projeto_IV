package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Provisionamento de usuário com senha (onboarding/bootstrap).
 * Difere do {@link CreateUser} por já definir a senha (BCrypt) e permissões.
 * REQUISITO de segurança: exposto publicamente apenas para o onboarding;
 * a criação autorizada será protegida no acesso (JWT) na implementação completa.
 */
@Service
public class ProvisionUser {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProvisionUser(TenantRepository tenantRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User provision(ProvisionUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String email = requireNonBlank(command.email(), "email");
        String name = requireNonBlank(command.name(), "name");
        HierarchyLevel level = Objects.requireNonNull(command.hierarchyLevel(), "hierarchyLevel");
        String password = requireNonBlank(command.password(), "password");

        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new TenantNotFoundException(tenantId);
        }

        if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        User user = User.create(UserId.random(), tenantId, email, name, level, command.departmentId());
        user.setPasswordHash(passwordEncoder.encode(password));
        if (command.permissions() != null) {
            command.permissions().forEach(user::grantPermission);
        }
        return userRepository.save(user);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
