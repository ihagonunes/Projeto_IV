package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Persiste os papéis de SISTEMA padrão (SPI-43/ADR-36): cria, para um tenant,
 * os cinco papéis (COLLABORATOR, LEADER, MANAGER, EXECUTIVE, TENANT_ADMIN) com
 * as permissões definidas por {@link SystemRole}.
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
        for (SystemRole systemRole : SystemRole.values()) {
            String name = systemRole.name();
            if (roleRepository.existsByTenantIdAndName(tenantId, name)) {
                continue;
            }
            Role role = Role.create(RoleId.random(), tenantId, name);
            systemRole.permissions().forEach(role::grantPermission);
            provisioned.add(roleRepository.save(role));
        }
        return provisioned;
    }
}
