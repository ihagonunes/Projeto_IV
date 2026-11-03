package br.com.qsoft.governance.engagement.application.port;

import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Fonte de fatos de engajamento. {@code ate} nulo significa:
 * operacional = hoje UTC; histórico = último dia da cobertura.
 */
public interface EngagementFactsPort {

    EngagementFacts load(TenantId tenantId, LocalDate ate);

    Optional<LocalDate> latestCoverage(TenantId tenantId);
}
