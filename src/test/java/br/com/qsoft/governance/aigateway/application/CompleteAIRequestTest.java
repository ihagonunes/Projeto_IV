package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.aigateway.infrastructure.provider.StubProviderClient;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Testes de {@link CompleteAIRequest} (TR01, TR02, TR03, TR08, TR09) com o
 * {@link StubProviderClient}. Sem Spring e sem banco: fakes em memória.
 */
class CompleteAIRequestTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();

    private final Map<UUID, RequestAttempt> attempts = new LinkedHashMap<>();
    private final List<Object> published = new ArrayList<>();
    private StubProviderClient stub;
    private ResolvedModel resolved;
    private ApplicationEventPublisher publisher;
    private AttemptRecorder recorder;
    private UsageDeliveryPublisher delivery;
    private CompleteAIRequest service;

    private final IdentityQuery identityQuery = new IdentityQuery() {
        @Override
        public Optional<UserContext> getUserContext(UserId id) {
            return Optional.of(new UserContext(id, tenantId, "COLLABORATOR", departmentId, "ACTIVE"));
        }
    };

    private ResolveModelForUser resolveModelForUser =
        new ResolveModelForUser(null, null, null, null) {
            @Override
            public ResolvedModel resolve(TenantId tenant, UserId user, UUID requestedModelId) {
                return resolved;
            }
        };

    private final RequestAttemptRepository attemptRepository = new RequestAttemptRepository() {
        @Override
        public RequestAttempt save(RequestAttempt attempt) {
            attempts.put(attempt.requestId(), attempt);
            return attempt;
        }

        @Override
        public Optional<RequestAttempt> findById(UUID requestId) {
            return Optional.ofNullable(attempts.get(requestId));
        }

        @Override
        public java.util.List<UUID> tenantsWithWork(java.time.Instant now,
                                                    java.time.Instant staleBefore) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<UUID> claimDueDeliveries(UUID tenantId, java.time.Instant now,
                                                       java.time.Instant leaseUntil, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<UUID> markStaleInFlight(UUID tenantId,
                                                      java.time.Instant staleBefore,
                                                      java.time.Instant now, int limit) {
            return java.util.List.of();
        }

        @Override
        public br.com.qsoft.governance.sharedkernel.query.PageResult<RequestAttempt> search(
                AttemptFilter filter, int page, int size) {
            return new br.com.qsoft.governance.sharedkernel.query.PageResult<>(
                java.util.List.of(), 0, page, size);
        }
    };

    @BeforeEach
    void setUp() {
        attempts.clear();
        published.clear();
        stub = new StubProviderClient();
        stub.reset();
        resolved = resolved("stub-echo");
        publisher = published::add;
        recorder = new AttemptRecorder(attemptRepository);
        delivery = new UsageDeliveryPublisher(publisher, attemptRepository, recorder);
        service = new CompleteAIRequest(identityQuery, resolveModelForUser, kind -> stub,
            recorder, delivery);
    }

    // ── TR01: sucesso ────────────────────────────────────────────────────────

    @Test
    void tr01_sucessoGravaTentativaEEntregaEvento() {
        CompletionResult result = service.complete(command("CHAT", "quatro palavras aqui"));

        assertThat(result.requestId()).isNotNull();
        assertThat(result.content()).isEqualTo("[stub] quatro palavras aqui");
        assertThat(result.inputTokens()).isEqualTo(3L);
        assertThat(result.outputTokens()).isEqualTo(3L);
        assertThat(result.costStatus()).isEqualTo("KNOWN");
        assertThat(result.estimatedCost()).isEqualByComparingTo("0.000120");

        RequestAttempt attempt = attempts.get(result.requestId());
        assertThat(attempt.authorizationStatus()).isEqualTo(AuthorizationStatus.GRANTED);
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.KNOWN);
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(attempt.departmentId()).isEqualTo(departmentId);
        assertThat(attempt.purpose()).isEqualTo(RequestPurpose.CHAT);

        assertThat(published).hasSize(1);
        assertThat(published.get(0)).isInstanceOfSatisfying(AIRequestCompleted.class,
            event -> assertThat(event.requestId()).isEqualTo(result.requestId()));
    }

    @Test
    void requestIdEhSempreGeradoNoServidor() {
        CompletionResult first = service.complete(command("CHAT", "a"));
        CompletionResult second = service.complete(command("CHAT", "a"));

        assertThat(first.requestId()).isNotNull();
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
    }

    @Test
    void tr08_usoDesconhecidoNaoProduzCustoNemEvento() {
        resolved = resolved("stub-unknown-usage");

        CompletionResult result = service.complete(command("CHAT", "a b"));

        assertThat(result.inputTokens()).isNull();
        assertThat(result.outputTokens()).isNull();
        assertThat(result.costStatus()).isEqualTo("INCONCLUSIVE");
        assertThat(result.estimatedCost()).isNull();

        RequestAttempt attempt = attempts.get(result.requestId());
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.INCONCLUSIVE);
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.NOT_APPLICABLE);
        assertThat(published).isEmpty();
    }

    @Test
    void precoAusenteComUsoConhecidoProduzInconclusivoSemEvento() {
        resolved = new ResolvedModel(modelId, "gpt-4o", "stub-echo", providerId, "OpenAI",
            ProviderKind.STUB, null, null, 30000, departmentId, null, null);

        CompletionResult result = service.complete(command("CHAT", "a b"));

        assertThat(result.inputTokens()).isEqualTo(2L);
        assertThat(result.outputTokens()).isEqualTo(2L);
        assertThat(result.costStatus()).isEqualTo("INCONCLUSIVE");
        assertThat(result.estimatedCost()).isNull();

        RequestAttempt attempt = attempts.get(result.requestId());
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.INCONCLUSIVE);
        assertThat(attempt.estimatedCost()).isNull();
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.NOT_APPLICABLE);
        assertThat(attempt.errorCode()).isEqualTo("PRICE_MISSING");
        assertThat(published).isEmpty();
    }

    @Test
    void tr08_zeroTokensConhecidosSaoDistintosDeUsoDesconhecido() {
        CompletionResult result = service.complete(command("CHAT", ""));

        RequestAttempt attempt = attempts.get(result.requestId());
        assertThat(attempt.inputTokens()).isZero();
        assertThat(attempt.outputTokens()).isZero();
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.KNOWN);
        assertThat(attempt.estimatedCost()).isEqualByComparingTo("0.000000");
    }

    // ── TR02: negado pela política ───────────────────────────────────────────

    @Test
    void tr02_modeloForaDaPoliticaGravaDeniedSemChamarProvider() {
        resolveModelForUserFailing(new ModelNotAllowedException());

        assertThatThrownBy(() -> service.complete(command("CHAT", "oi")))
            .isInstanceOf(ModelNotAllowedException.class);

        assertThat(attempts).hasSize(1);
        RequestAttempt attempt = attempts.values().iterator().next();
        assertThat(attempt.authorizationStatus()).isEqualTo(AuthorizationStatus.DENIED);
        assertThat(attempt.denialReason()).isEqualTo("MODEL_NOT_ALLOWED");
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.NOT_STARTED);
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.NOT_APPLICABLE);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.NOT_APPLICABLE);
        assertThat(attempt.estimatedCost()).isNull();
        assertThat(stub.lastCall()).isNull();
        assertThat(published).isEmpty();
    }

    // ── Ordem: IN_FLIGHT antes da chamada ───────────────────────────────────

    @Test
    void gravaInFlightAntesDeChamarOProvider() {
        boolean[] sawInFlight = {false};
        stub = new StubProviderClient() {
            @Override
            public ProviderResponse call(ProviderCall call) {
                sawInFlight[0] = attempts.values().stream()
                    .anyMatch(attempt -> attempt.executionStatus() == ExecutionStatus.IN_FLIGHT);
                return super.call(call);
            }
        };
        delivery = new UsageDeliveryPublisher(publisher, attemptRepository, recorder);
        service = new CompleteAIRequest(identityQuery, resolveModelForUser, kind -> stub,
            recorder, delivery);

        service.complete(command("CHAT", "oi"));

        assertThat(sawInFlight[0]).isTrue();
    }

    @Test
    void falhaAoGravarInFlightNaoChamaOProvider() {
        RequestAttemptRepository failing = new RequestAttemptRepository() {
            @Override
            public RequestAttempt save(RequestAttempt attempt) {
                throw new IllegalStateException("db down");
            }

            @Override
            public Optional<RequestAttempt> findById(UUID requestId) {
                return Optional.empty();
            }

            @Override
            public java.util.List<UUID> tenantsWithWork(java.time.Instant now,
                                                        java.time.Instant staleBefore) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<UUID> claimDueDeliveries(UUID tenantId, java.time.Instant now,
                                                           java.time.Instant leaseUntil,
                                                           int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<UUID> markStaleInFlight(UUID tenantId,
                                                          java.time.Instant staleBefore,
                                                          java.time.Instant now, int limit) {
                return java.util.List.of();
            }

            @Override
            public br.com.qsoft.governance.sharedkernel.query.PageResult<RequestAttempt> search(
                    AttemptFilter filter, int page, int size) {
                return new br.com.qsoft.governance.sharedkernel.query.PageResult<>(
                    java.util.List.of(), 0, page, size);
            }
        };
        service = new CompleteAIRequest(identityQuery, resolveModelForUser, kind -> stub,
            new AttemptRecorder(failing), new UsageDeliveryPublisher(publisher, failing,
                new AttemptRecorder(failing)));

        assertThatThrownBy(() -> service.complete(command("CHAT", "oi")))
            .isInstanceOf(AIAttemptNotRecordedException.class);

        assertThat(stub.lastCall()).isNull();
    }

    // ── TR03: timeout e erro ─────────────────────────────────────────────────

    @Test
    void tr03_timeoutGravaTimedOutSemEvento() {
        resolved = resolved("stub-timeout");

        assertThatThrownBy(() -> service.complete(command("CHAT", "oi")))
            .isInstanceOf(AIProviderTimeoutException.class);

        RequestAttempt attempt = attempts.values().iterator().next();
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.TIMED_OUT);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.INCONCLUSIVE);
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.NOT_APPLICABLE);
        assertThat(published).isEmpty();
    }

    @Test
    void erroDoProviderGravaFailedComCodigoCurto() {
        resolved = resolved("stub-error");

        assertThatThrownBy(() -> service.complete(command("CHAT", "oi")))
            .isInstanceOfSatisfying(AIProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("HTTP_500"));

        RequestAttempt attempt = attempts.values().iterator().next();
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.INCONCLUSIVE);
        assertThat(attempt.errorCode()).isEqualTo("HTTP_500");
        assertThat(published).isEmpty();
    }

    @Test
    void erroComCobrancaDeclaradaZeroProduzCustoKnownZero() {
        stub = new StubProviderClient() {
            @Override
            public ProviderResponse call(ProviderCall call) {
                throw new ProviderFailureException("HTTP_503", true);
            }
        };
        service = new CompleteAIRequest(identityQuery, resolveModelForUser, kind -> stub,
            recorder, delivery);

        assertThatThrownBy(() -> service.complete(command("CHAT", "oi")))
            .isInstanceOf(AIProviderFailureException.class);

        RequestAttempt attempt = attempts.values().iterator().next();
        assertThat(attempt.executionStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(attempt.costStatus()).isEqualTo(CostStatus.KNOWN);
        assertThat(attempt.estimatedCost()).isEqualByComparingTo("0.000000");
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    // ── Entrega (C1: sem reentrega) ──────────────────────────────────────────

    @Test
    void falhaDeEntregaDeixaPendingSemPropagarAoCliente() {
        publisher = event -> {
            throw new IllegalStateException("listener down");
        };
        delivery = new UsageDeliveryPublisher(publisher, attemptRepository, recorder);
        service = new CompleteAIRequest(identityQuery, resolveModelForUser, kind -> stub,
            recorder, delivery);

        CompletionResult result = service.complete(command("CHAT", "oi"));

        RequestAttempt attempt = attempts.get(result.requestId());
        assertThat(attempt.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(attempt.deliveryAttempts()).isEqualTo(1);
        assertThat(attempt.nextDeliveryAt()).isNotNull();
    }

    // ── TR09: preço fotografado ──────────────────────────────────────────────

    @Test
    void tr09_mudancaDePrecoNaoAlteraCustoHistorico() {
        CompletionResult first = service.complete(command("CHAT", "a"));
        RequestAttempt firstAttempt = attempts.get(first.requestId());
        BigDecimal firstCost = firstAttempt.estimatedCost();
        BigDecimal firstUnit = firstAttempt.unitPriceInput();

        resolved = new ResolvedModel(modelId, "gpt-4o", "stub-echo", providerId, "OpenAI",
            ProviderKind.STUB, null, null, 30000, departmentId,
            new BigDecimal("9.99"), new BigDecimal("9.99"));
        service.complete(command("CHAT", "a"));

        RequestAttempt reloaded = attempts.get(first.requestId());
        assertThat(reloaded.estimatedCost()).isEqualByComparingTo(firstCost);
        assertThat(reloaded.unitPriceInput()).isEqualByComparingTo(firstUnit);
    }

    // ── Validação ────────────────────────────────────────────────────────────

    @Test
    void purposeInvalidoNaoGravaTentativa() {
        assertThatThrownBy(() -> service.complete(command("INVALIDO", "oi")))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(attempts).isEmpty();
    }

    @Test
    void commandNuloEhRejeitado() {
        assertThatThrownBy(() -> service.complete(null))
            .isInstanceOf(NullPointerException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CompletionCommand command(String purpose, String userMessage) {
        return new CompletionCommand(tenantId, userId, modelId, purpose,
            List.of(CompletionMessage.user(userMessage)), List.of());
    }

    private ResolvedModel resolved(String externalName) {
        return new ResolvedModel(modelId, "gpt-4o", externalName, providerId, "OpenAI",
            ProviderKind.STUB, null, null, 30000, departmentId,
            new BigDecimal("0.01"), new BigDecimal("0.03"));
    }

    private void resolveModelForUserFailing(RuntimeException failure) {
        resolveModelForUser = new ResolveModelForUser(null, null, null, null) {
            @Override
            public ResolvedModel resolve(TenantId tenant, UserId user, UUID requestedModelId) {
                throw failure;
            }
        };
        // Recria o serviço com o resolutor que nega.
        service = new CompleteAIRequest(identityQuery, resolveModelForUser, kind -> stub,
            recorder, delivery);
    }
}
