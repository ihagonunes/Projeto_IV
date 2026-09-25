package br.com.qsoft.governance.department.api.query;

import br.com.qsoft.governance.department.api.model.DepartmentInfo;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público do módulo {@code department} para consulta organizacional.
 * <p>
 * Outros módulos consultam os dados do departamento aqui, em vez de acessar a
 * persistência do módulo (ADR-02/ADR-31). O {@code tenantId} é explícito para
 * preservar o isolamento (ADR-03).
 */
public interface OrganizationQuery {

    Optional<DepartmentInfo> findById(TenantId tenantId, UUID departmentId);
}
