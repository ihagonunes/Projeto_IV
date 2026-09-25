package br.com.qsoft.governance.department.application.port;

import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída do módulo {@code department} para persistência de Department.
 * <p>
 * Todas as consultas são <b>tenant-scoped</b>: o {@code tenantId} é parâmetro
 * obrigatório (vem sempre do contexto autenticado), nunca inferido do cliente.
 */
public interface DepartmentRepository {

    Department save(Department department);

    Optional<Department> findByTenantIdAndId(TenantId tenantId, UUID departmentId);

    List<Department> findByTenantId(TenantId tenantId);

    boolean existsByTenantIdAndName(TenantId tenantId, String name);

    /** Usado na edição: verifica duplicidade excluindo o próprio departamento. */
    boolean existsByTenantIdAndNameAndIdNot(TenantId tenantId, String name, UUID departmentId);
}
