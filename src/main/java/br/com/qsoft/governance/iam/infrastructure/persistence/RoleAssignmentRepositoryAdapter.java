package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de persistência que satisfaz a porta RoleAssignmentRepository.
 */
@Component
public class RoleAssignmentRepositoryAdapter implements RoleAssignmentRepository {

    private final RoleAssignmentJpaRepository jpaRepository;
    private final RoleAssignmentJpaMapper mapper;

    public RoleAssignmentRepositoryAdapter(RoleAssignmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = new RoleAssignmentJpaMapper();
    }

    @Override
    @Transactional
    public RoleAssignment save(RoleAssignment assignment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(assignment)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleAssignment> findById(RoleAssignmentId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignment> findByTenantIdAndUserId(TenantId tenantId, UserId userId) {
        return jpaRepository.findByTenantIdAndUserId(tenantId.value(), userId.value())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignment> findActiveByTenantIdAndUserId(TenantId tenantId, UserId userId) {
        return jpaRepository.findByTenantIdAndUserIdAndRevokedAtIsNull(tenantId.value(), userId.value())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void deleteById(RoleAssignmentId id) {
        jpaRepository.deleteById(id.value());
    }
}
