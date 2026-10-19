package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.exception.LastTenantAdminException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import org.springframework.stereotype.Service;

/**
 * Guarda do último {@code TENANT_ADMIN} ativo do tenant (ADR-37 item 5).
 * <p>
 * Usa <b>lock pessimista</b> na linha do papel {@code TENANT_ADMIN} do tenant
 * ({@code SELECT ... FOR UPDATE}) antes da contagem: revogações/desativações
 * concorrentes ficam serializadas e não zeram os administradores (fecha a
 * corrida apontada na revisão A1–A6, F3).
 */
@Service
public class TenantAdminGuard {

    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public TenantAdminGuard(RoleRepository roleRepository,
                            RoleAssignmentRepository roleAssignmentRepository) {
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    /**
     * Garante que remover a concessão ativa de {@code target} não deixa o tenant
     * sem administrador. Não faz nada se o alvo não for {@code TENANT_ADMIN}.
     */
    public void ensureCanRemoveTenantAdmin(TenantId tenantId, UserId target) {
        // Lock pessimista: serializa a checagem por tenant.
        Role adminRole = roleRepository.findByTenantIdAndNameForUpdate(tenantId, TENANT_ADMIN_ROLE)
            .orElseThrow(() -> new IllegalStateException(
                "Papel de sistema TENANT_ADMIN ausente no tenant."));

        boolean targetIsAdmin = roleAssignmentRepository
            .findActiveByTenantIdAndUserId(tenantId, target)
            .stream()
            .anyMatch(assignment -> assignment.roleId().equals(adminRole.id()));
        if (!targetIsAdmin) {
            return;
        }

        long activeAdmins = roleAssignmentRepository
            .countActiveByTenantIdAndRoleId(tenantId, adminRole.id());
        if (activeAdmins <= 1) {
            throw new LastTenantAdminException();
        }
    }
}
