package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Caso de uso de criação de Provider no catálogo do tenant (SPI-81). */
@Service
public class CreateProvider {

    private final ProviderRepository providerRepository;

    public CreateProvider(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    public Provider create(CreateProviderCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String name = requireName(command.name());

        if (providerRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ProviderNameAlreadyExistsException(name);
        }
        ProviderKind kind = command.kind() == null ? ProviderKind.STUB : command.kind();
        return providerRepository.save(Provider.create(UUID.randomUUID(), tenantId, name, kind,
            command.baseUrl(), command.credentialRef(), command.timeoutMs()));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        return name.trim();
    }
}
