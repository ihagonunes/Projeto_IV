package br.com.qsoft.governance.iam.domain.exception;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Lançada quando o tenant de destino não existe ao tentar uma operação de IAM.
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(TenantId tenantId) {
        super("Tenant não encontrado: " + tenantId + ".");
    }
}
