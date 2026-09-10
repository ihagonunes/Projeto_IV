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
import org.springframework.stereotype.Service;

/**
 * Caso de uso de cadastro de usuário dentro de um tenant.
 * O tenant de destino é validado e a unicidade de e-mail é por tenant.
 */
@Service
public class CreateUser {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public CreateUser(UserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    public User create(CreateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String email = requireNonBlank(command.email(), "email");
        String name = requireNonBlank(command.name(), "name");
        HierarchyLevel level = Objects.requireNonNull(command.hierarchyLevel(), "hierarchyLevel");

        // O usuário é criado dentro de um tenant válido.
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new TenantNotFoundException(tenantId);
        }

        // Unicidade de e-mail dentro do tenant (ADR-03).
        if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        User user = User.create(
            UserId.random(),
            tenantId,
            email,
            name,
            level,
            command.departmentId()
        );
        return userRepository.save(user);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}
