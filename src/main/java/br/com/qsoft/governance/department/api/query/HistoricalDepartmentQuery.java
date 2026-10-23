package br.com.qsoft.governance.department.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Set;
import java.util.UUID;

public interface HistoricalDepartmentQuery {
    Set<String> legacyIds(TenantId tenantId, Set<UUID> departmentIds);
}
