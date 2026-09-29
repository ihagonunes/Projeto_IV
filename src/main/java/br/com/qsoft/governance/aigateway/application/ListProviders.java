package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Caso de uso de listagem de Providers do tenant (SPI-81). */
@Service
public class ListProviders {

    private final ProviderRepository providerRepository;

    public ListProviders(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    public List<Provider> list(TenantId tenantId) {
        return providerRepository.findByTenantId(Objects.requireNonNull(tenantId, "tenantId"));
    }
}
