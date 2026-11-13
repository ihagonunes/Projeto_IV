package br.com.qsoft.governance.chat.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.api.model.AllowedModels;
import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.api.query.ModelResolutionQuery;
import br.com.qsoft.governance.chat.application.ChatMessageView;
import br.com.qsoft.governance.chat.application.ChatTurn;
import br.com.qsoft.governance.chat.application.DeleteConversation;
import br.com.qsoft.governance.chat.application.ListConversations;
import br.com.qsoft.governance.chat.application.ListMessages;
import br.com.qsoft.governance.chat.application.SendMessage;
import br.com.qsoft.governance.chat.application.SendMessageCommand;
import br.com.qsoft.governance.chat.application.StartConversation;
import br.com.qsoft.governance.chat.application.StartConversationCommand;
import br.com.qsoft.governance.chat.application.UpdateConversation;
import br.com.qsoft.governance.chat.application.UpdateConversationCommand;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationArchivedException;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.chat.domain.ConversationStatus;
import br.com.qsoft.governance.chat.domain.TurnInProgressOrFailedException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatConversationControllerTest {

    private static final String BASE = "/api/v1/conversas";
    private static final String MESSAGE = """
        {"mensagem":"Pergunta","clientMessageId":"turn-001"}
        """;
    private final TenantId tenant = TenantId.random();
    private final UserId owner = UserId.random();
    private final Conversation conversation = Conversation.start(UUID.randomUUID(), tenant, owner, "Pergunta");
    private final UUID id = conversation.id();
    private final ChatMessage user = ChatMessage.user(UUID.randomUUID(), tenant, id, "Pergunta");
    private final ChatMessage assistant = ChatMessage.assistant(UUID.randomUUID(), tenant, id, "Resposta", UUID.randomUUID());
    private final StartConversation start = mock(StartConversation.class);
    private final SendMessage send = mock(SendMessage.class);
    private final ListConversations list = mock(ListConversations.class);
    private final ListMessages messages = mock(ListMessages.class);
    private final UpdateConversation update = mock(UpdateConversation.class);
    private final DeleteConversation remove = mock(DeleteConversation.class);
    private final ModelResolutionQuery models = mock(ModelResolutionQuery.class);
    private final ChatConversationController controller =
        new ChatConversationController(start, send, list, messages, update, remove, models);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new ChatApiExceptionHandler()).build();

    @BeforeEach
    void setContext() {
        TenantContextHolder.set(new TenantContext(tenant, owner));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void startAndSendDistinguishNewTurnsFromReplays(boolean replayed) throws Exception {
        CompletionResult completion = replayed ? null : new CompletionResult(assistant.requestId(), "Resposta",
            List.of(), UUID.randomUUID(), "Modelo", UUID.randomUUID(), "Provider", 11L, 12L, null, "INCONCLUSIVE", 15);
        ChatTurn turn = new ChatTurn(conversation, user, assistant, completion, replayed);
        when(start.start(new StartConversationCommand(tenant, owner, "Pergunta", null, "turn-001"))).thenReturn(turn);
        when(send.send(new SendMessageCommand(tenant, owner, id, "Pergunta", null, "turn-001"))).thenReturn(turn);

        var created = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
            .andExpect(status().is(replayed ? 200 : 201))
            .andExpect(jsonPath("$.conversa.id").value(id.toString()))
            .andExpect(jsonPath("$.mensagens[0].role").value("USER"))
            .andExpect(jsonPath("$.mensagens[1].content").value("Resposta"));
        if (replayed) {
            created.andExpect(jsonPath("$.recibo").value(nullValue()));
        } else {
            created.andExpect(jsonPath("$.recibo.modelo").value("Modelo"))
                .andExpect(jsonPath("$.recibo.tokensEntrada").value(11))
                .andExpect(jsonPath("$.recibo.latenciaMs").value(15));
        }
        mvc.perform(post(BASE + "/{id}/mensagens", id).contentType(MediaType.APPLICATION_JSON).content(MESSAGE))
            .andExpect(status().is(replayed ? 200 : 201));
    }

    @Test
    void listsArchivedConversationsAndAllowedModelsForAuthenticatedActor() throws Exception {
        when(list.list(tenant, owner, ConversationStatus.ARCHIVED, "busca", 1, 5))
            .thenReturn(new PageResult<>(List.of(conversation.archive()), 6, 1, 5));
        when(models.allowedModels(tenant, owner)).thenReturn(AllowedModels.empty());

        mvc.perform(get(BASE).param("status", " archived ").param("q", "busca").param("page", "1").param("size", "5"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("ARCHIVED"))
            .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(BASE + "/modelos")).andExpect(status().isOk());
        verify(models).allowedModels(tenant, owner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETED", "unknown", " "})
    void rejectsInvalidListStatus(String value) throws Exception {
        mvc.perform(get(BASE).param("status", value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(list);
    }

    @Test
    void rejectsNullListStatusOnDirectInvocation() {
        assertThatThrownBy(() -> controller.list(null, null, 0, 20))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("status inválido.");
        verifyNoInteractions(list);
    }

    @Test
    void patchesTitleAndStatusAndDeletesThroughTheAuthenticatedContext() throws Exception {
        when(update.update(new UpdateConversationCommand(tenant, owner, id, "Novo", null)))
            .thenReturn(conversation.rename("Novo"));
        when(update.update(new UpdateConversationCommand(tenant, owner, id, null, "ARCHIVED")))
            .thenReturn(conversation.archive());
        when(update.update(new UpdateConversationCommand(tenant, owner, id, null, "ACTIVE")))
            .thenReturn(Conversation.restore(id, tenant, owner, "Novo", ConversationStatus.ACTIVE,
                conversation.createdAt(), conversation.updatedAt()));

        mvc.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Novo\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Novo"));
        mvc.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\" archived \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(delete(BASE + "/{id}", id)).andExpect(status().isNoContent());
        verify(remove).delete(tenant, owner, id);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 25L})
    void listsMessagesWithDurableReceiptAndHandlesMissingLatency(long expectedLatency) throws Exception {
        ReceiptInfo receipt = new ReceiptInfo(assistant.requestId(), UUID.randomUUID(), "Modelo", "Provider",
            11L, 12L, null, "INCONCLUSIVE", expectedLatency == 0 ? null : expectedLatency, "SUCCEEDED");
        when(messages.list(tenant, owner, id, 0, 50))
            .thenReturn(List.of(new ChatMessageView(user, null), new ChatMessageView(assistant, receipt)));

        mvc.perform(get(BASE + "/{id}/mensagens", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].recibo").value(nullValue()))
            .andExpect(jsonPath("$[1].recibo.requestId").value(assistant.requestId().toString()))
            .andExpect(jsonPath("$[1].recibo.latenciaMs").value(expectedLatency));
    }

    @Test
    void validatesRequestBodyAndRequiresAuthenticatedContext() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\":\" \"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("mensagem é obrigatória"));
        TenantContextHolder.clear();
        mvc.perform(get(BASE + "/modelos")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(start, models);
    }

    @ParameterizedTest
    @MethodSource("errors")
    void translatesUseCaseErrorsToStableHttpCodes(RuntimeException error, int httpStatus, String code) throws Exception {
        doThrow(error).when(remove).delete(tenant, owner, id);

        var result = mvc.perform(delete(BASE + "/{id}", id))
            .andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
        if (error instanceof TurnInProgressOrFailedException turn) {
            result.andExpect(jsonPath("$.messageId").value(turn.messageId().toString()));
        }
        if (error instanceof IllegalArgumentException) {
            result.andExpect(jsonPath("$.message").value("Dados inválidos."));
        }
    }

    static Stream<Arguments> errors() {
        return Stream.of(
            Arguments.of(new ConversationNotFoundException(), 404, "CONVERSATION_NOT_FOUND"),
            Arguments.of(new ConversationArchivedException(), 409, "CONVERSATION_ARCHIVED"),
            Arguments.of(new TurnInProgressOrFailedException(UUID.randomUUID(), null), 409, "TURN_IN_PROGRESS_OR_FAILED"),
            Arguments.of(new ModelNotAllowedException(), 403, "MODEL_NOT_ALLOWED"),
            Arguments.of(new NoModelConfiguredException(), 409, "NO_MODEL_CONFIGURED"),
            Arguments.of(new ModelUnavailableException(), 409, "MODEL_NOT_ALLOWED"),
            Arguments.of(new ProviderInactiveException(), 409, "NO_MODEL_CONFIGURED"),
            Arguments.of(new AIProviderTimeoutException(), 504, "PROVIDER_TIMEOUT"),
            Arguments.of(new AIProviderFailureException("TEST"), 502, "PROVIDER_FAILURE"),
            Arguments.of(new AIAttemptNotRecordedException(), 503, "ATTEMPT_NOT_RECORDED"),
            Arguments.of(new IllegalArgumentException(), 400, "INVALID_REQUEST"),
            Arguments.of(new IllegalArgumentException(" "), 400, "INVALID_REQUEST"));
    }
}
