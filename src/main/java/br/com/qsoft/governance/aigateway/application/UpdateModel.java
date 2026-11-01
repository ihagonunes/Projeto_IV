package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Edição administrativa do catálogo: nome, nome externo e preços (SPI-87). */
@Service
public class UpdateModel {

    private final ModelRepository modelRepository;

    public UpdateModel(ModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    public Model update(UpdateModelCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UUID modelId = Objects.requireNonNull(command.modelId(), "modelId");
        Model model = modelRepository.findByTenantIdAndId(tenantId, modelId)
            .orElseThrow(() -> new ModelNotFoundException(modelId));

        String name = command.name() == null ? null : command.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        if (!name.equals(model.name()) && modelRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ModelNameAlreadyExistsException(name);
        }
        Model updated = model.updateDetails(name, command.externalName(),
            command.inputPricePer1k(), command.outputPricePer1k());
        return modelRepository.save(updated);
    }
}
