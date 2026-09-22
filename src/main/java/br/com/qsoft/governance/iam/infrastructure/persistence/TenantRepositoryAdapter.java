package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.TenantDomainRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência que satisfaz as portas TenantRepository e
 * TenantDomainRepository do módulo IAM.
 */
@Component
public class TenantRepositoryAdapter implements TenantRepository, TenantDomainRepository {

    private final TenantJpaRepository jpaRepository;
    private final TenantJpaMapper mapper;

    public TenantRepositoryAdapter(TenantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new TenantJpaMapper();
    }

    @Override
    @Transactional
    public Tenant save(Tenant tenant) {
        TenantJpaEntity entity = jpaRepository.save(mapper.toEntity(tenant));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findById(TenantId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findByEmailDomain(String emailDomain) {
        if (emailDomain == null || emailDomain.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findByEmailDomain(Tenant.normalizeDomain(emailDomain))
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmailDomain(String emailDomain) {
        if (emailDomain == null || emailDomain.isBlank()) {
            return false;
        }
        return jpaRepository.existsByEmailDomain(Tenant.normalizeDomain(emailDomain));
    }
}
