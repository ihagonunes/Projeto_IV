package br.com.qsoft.governance.department.api.query;

import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público do módulo {@code department} para consulta da ModelPolicy.
 * <p>
 * O <b>aigateway</b> consome este contrato para aplicar o roteamento
 * determinístico (ADR-07) — ele não acessa a persistência do department.
 */
public interface DepartmentModelPolicyQuery {

    Optional<ModelPolicyInfo> findByDepartmentId(TenantId tenantId, UUID departmentId);
}
