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

    /**
     * Igual a {@link #findByTenantIdAndName}, mas com <b>lock pessimista</b> na
     * linha (usada pela guarda do último administrador). O default delega sem
     * lock para as implementações em memória (testes).
     */
    default Optional<Role> findByTenantIdAndNameForUpdate(TenantId tenantId, String name) {
        return findByTenantIdAndName(tenantId, name);
    }

    boolean existsByTenantIdAndName(TenantId tenantId, String name);
}
