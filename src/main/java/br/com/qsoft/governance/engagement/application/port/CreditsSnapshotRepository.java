package br.com.qsoft.governance.engagement.application.port;

import br.com.qsoft.governance.engagement.domain.CreditsItem;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Persistência imutável dos snapshots mensais de Credits (ADR-19/ADR-27). */
public interface CreditsSnapshotRepository {

    Map<UserId, CreditsItem> find(TenantId tenantId, SourceKind sourceKind, YearMonth month,
                                  String calculationVersion);

    /** Insere somente ausentes; nunca atualiza uma linha existente. */
    void insertIfAbsent(TenantId tenantId, SourceKind sourceKind, YearMonth month,
                        String calculationVersion, List<CreditsItem> items);
}
