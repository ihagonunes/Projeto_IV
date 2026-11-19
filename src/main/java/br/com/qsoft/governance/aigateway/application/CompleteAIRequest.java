package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient;
import br.com.qsoft.governance.aigateway.application.port.ProviderClientRegistry;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.application.port.ProviderTimeoutException;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelPriceRequiredException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import br.com.qsoft.governance.department.api.spending.SpendingBudget;
import br.com.qsoft.governance.department.api.spending.SpendingDeniedException;
import org.springframework.stereotype.Service;

/**
 * Implementação de {@link AICompletionService} no AI Gateway (ADR-11/ADR-38).
 * <p>
 * O {@code requestId} é sempre gerado aqui e o departamento vem do
 * {@link IdentityQuery} — o chamador não os informa. Antes de chamar o provider,
 * grava a tentativa {@code IN_FLIGHT} em transação própria ({@code REQUIRES_NEW});
 * o resultado é gravado depois. O conteúdo do prompt/resposta nunca é
 * persistido nem logado.
 */
@Service
public class CompleteAIRequest implements AICompletionService {

    private final IdentityQuery identityQuery;
    private final ResolveModelForUser resolveModelForUser;
    private final ProviderClientRegistry providerClientRegistry;
    private final AttemptRecorder attemptRecorder;
    private final UsageDeliveryPublisher deliveryPublisher;
    private final SpendingBudget spendingBudget;

    public CompleteAIRequest(IdentityQuery identityQuery,
                             ResolveModelForUser resolveModelForUser,
                             ProviderClientRegistry providerClientRegistry,
                             AttemptRecorder attemptRecorder,
                             UsageDeliveryPublisher deliveryPublisher, SpendingBudget spendingBudget) {
        this.identityQuery = identityQuery;
        this.resolveModelForUser = resolveModelForUser;
        this.providerClientRegistry = providerClientRegistry;
        this.attemptRecorder = attemptRecorder;
        this.deliveryPublisher = deliveryPublisher;
        this.spendingBudget = spendingBudget;
    }

    @Override
    public CompletionResult complete(CompletionCommand command) {
        Objects.requireNonNull(command, "command é obrigatório.");
        TenantId tenantId = command.tenantId();
        UserId userId = command.userId();
        RequestPurpose purpose = parsePurpose(command.purpose());
        UUID requestId = UUID.randomUUID();
        UUID departmentId = departmentIdOf(tenantId, userId);

        ResolvedModel resolved;
        try {
            resolved = resolveModelForUser.resolve(tenantId, userId, command.requestedModelId());
        } catch (RuntimeException ex) {
            recordDenied(requestId, tenantId, userId, departmentId, purpose,
                command.requestedModelId(), denialReason(ex));
            throw ex;
        }

        // Cada byte UTF-8 é uma cota conservadora para os tokenizers suportados.
        long inputBound = 256;
        for (var message : command.messages()) {
            inputBound += 64 + (message.content() == null ? 0 : message.content().getBytes(StandardCharsets.UTF_8).length);
            inputBound += message.role().getBytes(StandardCharsets.UTF_8).length;
            inputBound += message.toolCallId() == null ? 0 : message.toolCallId().getBytes(StandardCharsets.UTF_8).length;
        }
        for (var tool : command.tools()) {
            inputBound += 128 + (tool.toString().getBytes(StandardCharsets.UTF_8).length * 2L);
        }
        final int outputLimit = 2048;
        try {
            if (resolved.inputPricePer1k() == null || resolved.outputPricePer1k() == null) {
                throw new SpendingDeniedException("SPENDING_PRICE_MISSING", "Os preços deste modelo precisam ser configurados.");
            }
            spendingBudget.reserve(tenantId, userId, resolved.modelId(), requestId,
                resolved.estimatedCost(inputBound, outputLimit));
        } catch (SpendingDeniedException ex) {
            recordDenied(requestId, tenantId, userId, departmentId, purpose, resolved.modelId(), ex.code());
            throw ex;
        }
        try {
            recordInFlight(requestId, tenantId, userId, purpose, resolved);
        } catch (RuntimeException ex) {
            spendingBudget.settle(tenantId, requestId, BigDecimal.ZERO);
            throw ex;
        }

        long startedAt = System.nanoTime();
        ProviderClient.ProviderResponse response;
        try {
            response = clientFor(resolved).call(new ProviderClient.ProviderCall(
                resolved.providerKind(), resolved.baseUrl(), resolved.credentialRef(),
                resolved.timeoutMs(), resolved.externalModelName(), command.messages(),
                command.tools(), outputLimit));
        } catch (ProviderTimeoutException ex) {
            long latencyMs = elapsed(startedAt);
            recordSafely(() -> attemptRecorder.recordTimeout(requestId, latencyMs));
            throw new AIProviderTimeoutException();
        } catch (ProviderFailureException ex) {
            if (ex.billingDeclaredZero()) spendingBudget.settle(tenantId, requestId, BigDecimal.ZERO);
            long latencyMs = elapsed(startedAt);
            recordSafely(() -> attemptRecorder.recordFailure(
                requestId, ex.errorCode(), latencyMs, ex.billingDeclaredZero()));
            // Custo KNOWN (cobrança declarada zero) gera uso PENDING; entrega sem reentrega.
            deliveryPublisher.deliver(requestId);
            throw new AIProviderFailureException(ex.errorCode());
        }

        long latencyMs = elapsed(startedAt);
        Usage usage = usageOf(response, resolved);
        if (usage.estimatedCost() != null) {
            spendingBudget.settle(tenantId, requestId, usage.estimatedCost());
        }
        try {
            attemptRecorder.recordSuccess(requestId, usage.inputTokens(), usage.outputTokens(),
                usage.costStatus(), usage.estimatedCost(), latencyMs, usage.deliveryStatus(),
                usage.errorCode());
        } catch (RuntimeException ex) {
            throw new AIAttemptNotRecordedException();
        }

        if (usage.deliveryStatus() == DeliveryStatus.PENDING) {
            deliveryPublisher.deliver(requestId);
        }

        return new CompletionResult(requestId, response.content(), response.toolCalls(),
            resolved.modelId(), resolved.modelName(), resolved.providerId(), resolved.providerName(),
            usage.inputTokens(), usage.outputTokens(), usage.estimatedCost(),
            usage.costStatus().name(), latencyMs);
    }

    private void recordDenied(UUID requestId, TenantId tenantId, UserId userId, UUID departmentId,
                              RequestPurpose purpose, UUID requestedModelId, String reason) {
        recordSafely(() -> attemptRecorder.recordDenied(requestId, tenantId.value(), userId.value(),
            departmentId, purpose, requestedModelId, null, reason));
    }

    private void recordInFlight(UUID requestId, TenantId tenantId, UserId userId,
                                RequestPurpose purpose, ResolvedModel resolved) {
        try {
            attemptRecorder.recordInFlight(requestId, tenantId.value(), userId.value(),
                resolved.departmentId(), purpose, resolved.modelId(), resolved.providerId(),
                resolved.inputPricePer1k(), resolved.outputPricePer1k());
        } catch (RuntimeException ex) {
            throw new AIAttemptNotRecordedException();
        }
    }

    private ProviderClient clientFor(ResolvedModel resolved) {
        return providerClientRegistry.forKind(resolved.providerKind());
    }

    private static Usage usageOf(ProviderClient.ProviderResponse response, ResolvedModel resolved) {
        if (response.usage() instanceof ProviderClient.Usage.Known known) {
            try {
                BigDecimal estimatedCost = resolved.estimatedCost(
                    known.inputTokens(), known.outputTokens());
                return new Usage(known.inputTokens(), known.outputTokens(), CostStatus.KNOWN,
                    estimatedCost, DeliveryStatus.PENDING, null);
            } catch (ModelPriceRequiredException ex) {
                return new Usage(known.inputTokens(), known.outputTokens(),
                    CostStatus.INCONCLUSIVE, null, DeliveryStatus.NOT_APPLICABLE, "PRICE_MISSING");
            }
        }
        return new Usage(null, null, CostStatus.INCONCLUSIVE, null,
            DeliveryStatus.NOT_APPLICABLE, null);
    }

    private UUID departmentIdOf(TenantId tenantId, UserId userId) {
        return identityQuery.getUserContext(userId)
            .filter(context -> context.tenantId().equals(tenantId))
            .map(UserContext::departmentId)
            .orElse(null);
    }

    private static RequestPurpose parsePurpose(String purpose) {
        try {
            return RequestPurpose.valueOf(purpose.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("purpose inválido.");
        }
    }

    private static String denialReason(RuntimeException ex) {
        if (ex instanceof ModelNotAllowedException) {
            return "MODEL_NOT_ALLOWED";
        }
        if (ex instanceof NoModelConfiguredException) {
            return "NO_MODEL_CONFIGURED";
        }
        if (ex instanceof ModelUnavailableException) {
            return "MODEL_UNAVAILABLE";
        }
        if (ex instanceof ProviderInactiveException) {
            return "PROVIDER_INACTIVE";
        }
        if (ex instanceof ModelNotFoundException) {
            return "MODEL_NOT_FOUND";
        }
        if (ex instanceof ProviderNotFoundException) {
            return "PROVIDER_NOT_FOUND";
        }
        return "DENIED";
    }

    private static long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static void recordSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // A gravação do resultado é best-effort: a tentativa pode ficar
            // IN_FLIGHT e ser resolvida como OUTCOME_UNKNOWN pelo job (C2).
        }
    }

    private record Usage(Long inputTokens, Long outputTokens, CostStatus costStatus,
                         BigDecimal estimatedCost, DeliveryStatus deliveryStatus, String errorCode) {
    }
}
