package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída do módulo IAM para persistência das atribuições de papel (policy).
 */
public interface RoleAssignmentRepository {

    RoleAssignment save(RoleAssignment assignment);

    Optional<RoleAssignment> findById(RoleAssignmentId id);

    List<RoleAssignment> findByTenantIdAndUserId(TenantId tenantId, UserId userId);

    void deleteById(RoleAssignmentId id);
}
