package br.com.qsoft.governance.observability.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Contrato público do módulo {@code observability} para os dias com uso
 * operacional por usuário (ADR-40 §1).
 *
 * <p>A implementação filtra sempre por {@code tenant_id} e calcula o dia em UTC.
 * O intervalo {@code [from, to]} é inclusivo nas duas pontas.
 */
public interface UsageActivityQuery {

    Map<UserId, Set<LocalDate>> activeDays(TenantId tenantId, Set<UserId> userIds,
                                           LocalDate from, LocalDate to);
}
