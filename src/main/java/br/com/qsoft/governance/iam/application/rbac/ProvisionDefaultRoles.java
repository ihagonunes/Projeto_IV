package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.PermissionMatrix;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Persiste a matriz de permissões PADRÃO (SPI-43): cria, para um tenant, um
 * papel por nível hierárquico (COLLABORATOR, LEADER, MANAGER, EXECUTIVE) com as
 * permissões definidas pela {@link PermissionMatrix}.
 *
 * Operação idempotente por (tenant, nome): não recria papéis já existentes.
 */
@Service
public class ProvisionDefaultRoles {

    private final RoleRepository roleRepository;

    public ProvisionDefaultRoles(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> provision(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");

        List<Role> provisioned = new ArrayList<>();
        for (HierarchyLevel level : HierarchyLevel.values()) {
            String name = level.name();
            if (roleRepository.existsByTenantIdAndName(tenantId, name)) {
                continue;
            }
            Role role = Role.create(RoleId.random(), tenantId, name);
            PermissionMatrix.permissionsFor(level).forEach(role::grantPermission);
            provisioned.add(roleRepository.save(role));
        }
        return provisioned;
    }
}
