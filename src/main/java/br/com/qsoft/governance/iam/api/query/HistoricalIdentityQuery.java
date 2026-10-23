package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;

/** Identidade legada validada pelo IAM; não concede permissões. */
public interface HistoricalIdentityQuery {
    Optional<String> employeeId(TenantId tenantId, UserId userId);
}
