package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Caso de uso de ativação/desativação de Model no catálogo (SPI-81). */
@Service
public class ChangeModelStatus {

    private final ModelRepository modelRepository;

    public ChangeModelStatus(ModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    public Model change(ChangeModelStatusCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UUID modelId = Objects.requireNonNull(command.modelId(), "modelId");
        ModelStatus status = Objects.requireNonNull(command.status(), "status");

        Model model = modelRepository.findByTenantIdAndId(tenantId, modelId)
            .orElseThrow(() -> new ModelNotFoundException(modelId));

        Model updated = status == ModelStatus.AVAILABLE ? model.makeAvailable() : model.disable();
        return modelRepository.save(updated);
    }
}
