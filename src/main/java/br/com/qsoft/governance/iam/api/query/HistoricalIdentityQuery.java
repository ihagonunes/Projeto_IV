package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Identidade legada validada pelo IAM; não concede permissões. */
public interface HistoricalIdentityQuery {
    Optional<String> employeeId(TenantId tenantId, UserId userId);

    /** Resolve vários vínculos em lote, sempre filtrando o tenant. */
    Map<UserId, String> employeeIds(TenantId tenantId, Set<UserId> userIds);
}
