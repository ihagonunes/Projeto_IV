package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;

/**
 * Porta de saída do módulo IAM para persistência de papéis (Role).
 */
public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findById(RoleId id);

    Optional<Role> findByTenantIdAndName(TenantId tenantId, String name);

    boolean existsByTenantIdAndName(TenantId tenantId, String name);
}
