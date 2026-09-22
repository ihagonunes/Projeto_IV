package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de leitura dos dados cadastrais do tenant atual (SPI-52).
 * O tenant é SEMPRE o do contexto autenticado (ADR-03).
 */
@Service
public class GetCurrentTenant {

    private final TenantRepository tenantRepository;

    public GetCurrentTenant(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant current(TenantId tenantId) {
        TenantId id = Objects.requireNonNull(tenantId, "tenantId");
        return tenantRepository.findById(id)
            .orElseThrow(() -> new TenantNotFoundException(id));
    }
}
