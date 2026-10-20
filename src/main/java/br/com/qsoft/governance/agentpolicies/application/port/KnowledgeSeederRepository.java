package br.com.qsoft.governance.agentpolicies.application.port;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistência do seeder de conhecimento (idempotente por título). */
public interface KnowledgeSeederRepository {

    boolean existsByTitle(TenantId tenantId, String title);

    void seed(TenantId tenantId, String title, boolean tenantWide, Set<UUID> departmentIds,
              List<String> chunks);
}
