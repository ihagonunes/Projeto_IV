package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de criação de Model no catálogo (SPI-81). O Provider referenciado
 * precisa existir <b>no mesmo tenant</b> (ADR-09).
 */
@Service
public class CreateModel {

    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;

    public CreateModel(ModelRepository modelRepository, ProviderRepository providerRepository) {
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
    }

    public Model create(CreateModelCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UUID providerId = Objects.requireNonNull(command.providerId(), "providerId");
        String name = requireName(command.name());

        providerRepository.findByTenantIdAndId(tenantId, providerId)
            .orElseThrow(() -> new ProviderNotFoundException(providerId));

        if (modelRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ModelNameAlreadyExistsException(name);
        }
        return modelRepository.save(Model.create(UUID.randomUUID(), tenantId, providerId, name,
            command.externalName(), command.inputPricePer1k(), command.outputPricePer1k()));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        return name.trim();
    }
}
