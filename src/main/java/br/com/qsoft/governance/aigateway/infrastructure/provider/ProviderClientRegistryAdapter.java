package br.com.qsoft.governance.aigateway.infrastructure.provider;

import br.com.qsoft.governance.aigateway.application.port.ProviderClient;
import br.com.qsoft.governance.aigateway.application.port.ProviderClientRegistry;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registro dos adaptadores disponíveis, indexado por {@link ProviderKind}. */
@Component
public class ProviderClientRegistryAdapter implements ProviderClientRegistry {

    private final Map<ProviderKind, ProviderClient> clients;

    public ProviderClientRegistryAdapter(StubProviderClient stub,
                                         OpenAiCompatibleProviderClient openAiCompatible) {
        Map<ProviderKind, ProviderClient> registry = new EnumMap<>(ProviderKind.class);
        registry.put(ProviderKind.STUB, stub);
        registry.put(ProviderKind.OPENAI_COMPATIBLE, openAiCompatible);
        this.clients = Map.copyOf(registry);
    }

    @Override
    public ProviderClient forKind(ProviderKind kind) {
        ProviderClient client = kind == null ? null : clients.get(kind);
        if (client == null) {
            throw new ProviderFailureException("UNSUPPORTED_PROVIDER");
        }
        return client;
    }
}
