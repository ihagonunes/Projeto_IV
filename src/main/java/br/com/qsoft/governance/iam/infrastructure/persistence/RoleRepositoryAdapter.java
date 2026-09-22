package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência que satisfaz a porta RoleRepository do módulo IAM.
 */
@Component
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;
    private final RoleJpaMapper mapper;

    public RoleRepositoryAdapter(RoleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new RoleJpaMapper();
    }

    @Override
    @Transactional
    public Role save(Role role) {
        RoleJpaEntity entity = jpaRepository.save(mapper.toEntity(role));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(RoleId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByTenantIdAndName(TenantId tenantId, String name) {
        return jpaRepository.findByTenantIdAndName(tenantId.value(), name).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
        return jpaRepository.existsByTenantIdAndName(tenantId.value(), name);
    }
}
