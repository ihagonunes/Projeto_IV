package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import java.util.Optional;

/**
 * Porta de LEITURA (query) do módulo IAM para resolução de tenant pelo domínio
 * de e-mail corporativo (SPI-54 / ADR-32).
 * <p>
 * Separada de {@link TenantRepository} para manter o domínio como chave de
 * resolução isolada da persistência do agregado.
 */
public interface TenantDomainRepository {

    /** Tenant cujo domínio de e-mail corporativo cadastrado é exatamente este. */
    Optional<Tenant> findByEmailDomain(String emailDomain);

    boolean existsByEmailDomain(String emailDomain);
}
