package br.com.qsoft.governance.aigateway.api.query;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta em lote do recibo de execução (ADR-42): só devolve tentativas do
 * tenant cujo {@code user_id} é o dono. Máximo de 100 ids por chamada.
 */
public interface AttemptReceiptQuery {

    Map<UUID, ReceiptInfo> receipts(TenantId tenantId, UserId owner, Set<UUID> requestIds);
}
