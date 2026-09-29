package br.com.qsoft.governance.department.application.port;

import br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída do módulo {@code department} para persistência da ModelPolicy.
 * As consultas são tenant-scoped (o tenant é sempre explícito — ADR-03).
 */
public interface ModelPolicyRepository {

    ModelPolicy save(ModelPolicy policy);

    Optional<ModelPolicy> findByTenantIdAndDepartmentId(TenantId tenantId, UUID departmentId);
}
