package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Caso de uso de listagem de Models do tenant (SPI-81). */
@Service
public class ListModels {

    private final ModelRepository modelRepository;

    public ListModels(ModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    public List<Model> list(TenantId tenantId) {
        return modelRepository.findByTenantId(Objects.requireNonNull(tenantId, "tenantId"));
    }

    public List<Model> availableForDashboard(TenantId tenantId) {
        return list(tenantId).stream()
            .filter(Model::isAvailable)
            .toList();
    }
}
