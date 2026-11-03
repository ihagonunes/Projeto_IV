package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.sharedkernel.id.RoleAssignmentId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Porta de saída do módulo IAM para persistência das atribuições de papel (policy).
 */
public interface RoleAssignmentRepository {

    RoleAssignment save(RoleAssignment assignment);

    Optional<RoleAssignment> findById(RoleAssignmentId id);

    List<RoleAssignment> findByTenantIdAndUserId(TenantId tenantId, UserId userId);

    /** Concessões ativas (não revogadas) do usuário no tenant (ADR-36). */
    List<RoleAssignment> findActiveByTenantIdAndUserId(TenantId tenantId, UserId userId);

    /** Contagem de concessões ativas do tenant para um papel (guarda do último admin). */
    long countActiveByTenantIdAndRoleId(TenantId tenantId, RoleId roleId);

    /**
     * Usuários cujas concessões ativas são <b>somente</b> {@code TENANT_ADMIN}
     * (ADR-40 §3). Quem não tem nenhuma concessão ativa não entra neste conjunto.
     */
    default Set<UserId> findUserIdsWithOnlyTenantAdminGrant(TenantId tenantId) {
        throw new UnsupportedOperationException("findUserIdsWithOnlyTenantAdminGrant não implementado");
    }

    void deleteById(RoleAssignmentId id);
}
