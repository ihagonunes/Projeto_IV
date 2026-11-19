package br.com.qsoft.governance.department.api.spending;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.util.UUID;

/** Contrato financeiro para o Gateway. Tenant e usuário são contexto interno autenticado. */
public interface SpendingBudget {
    void reserve(TenantId tenant, UserId user, UUID model, UUID request, BigDecimal maximumCost);
    void settle(TenantId tenant, UUID request, BigDecimal actualCost);
}
