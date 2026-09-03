package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidCredentialsException;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de autenticação: resolve o tenant pelo slug, verifica o usuário,
 * confere a senha (BCrypt) e exige Tenant/User ACTIVE. Qualquer falha resulta
 * em erro genérico (ADR-30).
 */
@Service
public class AuthenticateUser {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticateUser(TenantRepository tenantRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUser login(LoginCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        String tenantSlug = command.tenantSlug().trim();
        String email = command.email().trim();
        String password = command.password();

        // Resolve o tenant pelo slug (o e-mail não é global).
        var tenant = tenantRepository.findBySlug(tenantSlug).orElse(null);
        if (tenant == null || tenant.status() != TenantStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        var user = userRepository.findByTenantIdAndEmail(tenant.id(), email).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        if (!user.hasPasswordHash() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return new AuthenticatedUser(user.id(), user.tenantId(), user.email(), user.hierarchyLevel().name());
    }
}
