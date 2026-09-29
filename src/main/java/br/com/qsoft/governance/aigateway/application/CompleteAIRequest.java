package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.application.port.ProviderRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.Provider;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Conclusão de uma requisição de IA no Gateway (SPI-70/72): coleta as métricas
 * da completion, calcula o custo estimado <b>uma vez</b> e emite
 * {@link AIRequestCompleted} — a <b>única origem operacional</b> de UsageRecord
 * (ADR-13).
 * <p>
 * A publicação é um evento de domínio <b>in-process</b> (ADR-02): o
 * observability consome como <b>efeito secundário</b> e uma falha dele
 * <b>não</b> invalida a completion (SPI-74). Não existe endpoint público
 * genérico de logs (SPI-71).
 */
@Service
public class CompleteAIRequest {

    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteAIRequest(ModelRepository modelRepository,
                             ProviderRepository providerRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
        this.eventPublisher = eventPublisher;
    }

    public AIRequestCompleted complete(CompleteAIRequestCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UUID modelId = Objects.requireNonNull(command.modelId(), "modelId");
        UUID requestId = command.requestId() == null ? UUID.randomUUID() : command.requestId();

        Model model = modelRepository.findByTenantIdAndId(tenantId, modelId)
            .orElseThrow(() -> new ModelNotFoundException(modelId));
        if (!model.isAvailable()) {
            throw new ModelUnavailableException();
        }
        Provider provider = providerRepository.findByTenantIdAndId(tenantId, model.providerId())
            .orElseThrow(() -> new ProviderNotFoundException(model.providerId()));
        if (!provider.isActive()) {
            throw new ProviderInactiveException();
        }

        AIRequestCompleted event = new AIRequestCompleted(
            requestId,
            tenantId.value(),
            command.departmentId(),
            command.userId(),
            model.id(),
            provider.id(),
            command.inputTokens(),
            command.outputTokens(),
            model.estimatedCost(command.inputTokens(), command.outputTokens()),
            command.latencyMs(),
            command.status() == null ? "COMPLETED" : command.status()
        );

        // Efeito secundário: uma falha do consumidor não invalida a completion (SPI-74).
        eventPublisher.publishEvent(event);
        return event;
    }
}
