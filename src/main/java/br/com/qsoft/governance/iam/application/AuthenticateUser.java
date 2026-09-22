package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantDomainRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.InvalidCredentialsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de autenticação: resolve o tenant, verifica o usuário, confere a
 * senha (BCrypt) e exige Tenant/User ACTIVE. Qualquer falha resulta em erro
 * genérico (ADR-30).
 * <p>
 * Resolução do tenant quando o {@code tenantSlug} não é informado (ADR-32,
 * emendado pela SPI-54): primeiro pelo domínio de e-mail corporativo
 * <b>cadastrado</b> no tenant — determinístico, já que o domínio é único
 * globalmente; se nenhum tenant tiver o domínio cadastrado, mantém-se o
 * fallback legado do primeiro rótulo do domínio.
 */
@Service
public class AuthenticateUser {

    private final TenantRepository tenantRepository;
    private final TenantDomainRepository tenantDomainRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticateUser(TenantRepository tenantRepository,
                            TenantDomainRepository tenantDomainRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.tenantDomainRepository = tenantDomainRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUser login(LoginCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        String email = command.email().trim();
        String password = command.password();
        String tenantSlug = command.tenantSlug() == null ? "" : command.tenantSlug().trim();

        Tenant tenant = resolveTenant(tenantSlug, email);
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

    /**
     * Resolve o tenant: slug explícito; domínio de e-mail cadastrado; ou, em
     * último caso, o primeiro rótulo do domínio como slug (comportamento legado).
     */
    private Tenant resolveTenant(String tenantSlug, String email) {
        if (!tenantSlug.isEmpty()) {
            return tenantRepository.findBySlug(tenantSlug).orElse(null);
        }

        String domain = domainOf(email);
        if (domain != null) {
            var byDomain = tenantDomainRepository.findByEmailDomain(domain);
            if (byDomain.isPresent()) {
                return byDomain.get();
            }
        }

        String derivedSlug = deriveTenantSlug(email);
        return derivedSlug.isEmpty() ? null : tenantRepository.findBySlug(derivedSlug).orElse(null);
    }

    /** Domínio do e-mail (parte após '@'), normalizado; {@code null} se ausente. */
    private static String domainOf(String email) {
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return null;
        }
        return Tenant.normalizeDomain(email.substring(at + 1));
    }

    /**
     * Deriva um slug de tenant a partir do domínio do e-mail (primeiro rótulo).
     * Ex.: admin@qsoft.com → "qsoft". Retorna "" se não for possível derivar.
     */
    private static String deriveTenantSlug(String email) {
        String domain = domainOf(email);
        if (domain == null) {
            return "";
        }
        int dot = domain.indexOf('.');
        return dot > 0 ? domain.substring(0, dot) : domain;
    }
}
