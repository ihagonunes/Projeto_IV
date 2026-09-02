package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência que satisfaz a porta UserRepository do módulo IAM.
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserJpaMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new UserJpaMapper();
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = jpaRepository.save(mapper.toEntity(user));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
        return jpaRepository.findByTenantIdAndEmail(tenantId.value(), email).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
        return jpaRepository.existsByTenantIdAndEmail(tenantId.value(), email);
    }
}
