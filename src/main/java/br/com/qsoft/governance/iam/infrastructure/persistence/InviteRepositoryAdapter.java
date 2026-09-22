package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência que satisfaz a porta InviteRepository.
 */
@Component
public class InviteRepositoryAdapter implements InviteRepository {

    private final InviteJpaRepository jpaRepository;
    private final InviteJpaMapper mapper;

    public InviteRepositoryAdapter(InviteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new InviteJpaMapper();
    }

    @Override
    @Transactional
    public Invite save(Invite invite) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(invite)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invite> findById(InviteId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invite> findByToken(String token) {
        return jpaRepository.findByToken(token).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByToken(String token) {
        return jpaRepository.existsByToken(token);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invite> findByTenantId(TenantId tenantId) {
        return jpaRepository.findByTenantId(tenantId.value()).stream().map(mapper::toDomain).toList();
    }
}
