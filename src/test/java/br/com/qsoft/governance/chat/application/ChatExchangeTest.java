package br.com.qsoft.governance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.api.completion.AICompletionService;
import br.com.qsoft.governance.aigateway.api.completion.CompletionCommand;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.query.AttemptReceiptQuery;
import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationArchivedException;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Fluxo de envio: ordem das gravações, falha do provider e histórico. */
class ChatExchangeTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId owner = UserId.random();
    private final UUID modelId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    private final Map<UUID, Conversation> conversations = new LinkedHashMap<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<CompletionCommand> completions = new ArrayList<>();

    private RuntimeException failure;
    private CompletionResult result = new CompletionResult(UUID.randomUUID(), "[stub] oi", List.of(),
        modelId, "gpt-4o", providerId, "OpenAI", 3L, 3L, new BigDecimal("0.000120"), "KNOWN", 42L);

    private final ConversationRepository conversationRepository = new ConversationRepository() {
        @Override
        public Conversation save(Conversation conversation) {
            conversations.put(conversation.id(), conversation);
            return conversation;
        }

        @Override
        public Optional<Conversation> findByTenantIdAndIdAndOwner(TenantId tid, UUID id,
                                                                  UserId actor) {
            return Optional.ofNullable(conversations.get(id))
                .filter(c -> c.tenantId().equals(tid) && c.isOwnedBy(actor));
        }

        @Override
        public PageResult<Conversation> findByOwner(TenantId tid, UserId actor, int page, int size) {
            List<Conversation> owned = conversations.values().stream()
                .filter(c -> c.tenantId().equals(tid) && c.isOwnedBy(actor))
                .toList();
            return new PageResult<>(owned, owned.size(), page, size);
        }
    };

    private final ChatMessageRepository messageRepository = new ChatMessageRepository() {
        @Override
        public ChatMessage save(ChatMessage message) {
            messages.add(message);
            return message;
        }

        @Override
        public List<ChatMessage> findLastMessages(TenantId tid, UUID conversationId, UserId actor,
                                                  int limit) {
            List<ChatMessage> owned = messages.stream()
                .filter(m -> m.conversationId().equals(conversationId))
                .toList();
            int from = Math.max(0, owned.size() - limit);
            return owned.subList(from, owned.size());
        }

        @Override
        public List<ChatMessage> findByConversation(TenantId tid, UUID conversationId,
                                                    UserId actor) {
            return messages.stream().filter(m -> m.conversationId().equals(conversationId)).toList();
        }
    };

    private final AICompletionService completionService = command -> {
        completions.add(command);
        if (failure != null) {
            throw failure;
        }
        return result;
    };

    private final AttemptReceiptQuery receiptQuery = (tenantId, owner, ids) -> Map.of();
    private final ChatMessageWriter writer =
        new ChatMessageWriter(conversationRepository, messageRepository);
    private final ChatExchange exchange =
        new ChatExchange(writer, messageRepository, completionService);
    private final ChatTurnReplay replay = new ChatTurnReplay(messageRepository, receiptQuery);
    private final SendMessage sendMessage =
        new SendMessage(conversationRepository, messageRepository, exchange, replay);
    private final ListMessages listMessages =
        new ListMessages(conversationRepository, messageRepository, receiptQuery);

    @Test
    void enviaMensagemEGravaARespostaComRequestId() {
        Conversation conversation = persistedConversation(owner);

        ChatTurn turn = sendMessage.send(new SendMessageCommand(
            tenantId, owner, conversation.id(), "olá", modelId, null));

        assertThat(messages).hasSize(2);
        assertThat(turn.userMessage().role().name()).isEqualTo("USER");
        assertThat(turn.assistantMessage().role().name()).isEqualTo("ASSISTANT");
        assertThat(turn.assistantMessage().requestId()).isEqualTo(result.requestId());
        assertThat(completions).hasSize(1);
        assertThat(completions.get(0).purpose()).isEqualTo("CHAT");
        assertThat(completions.get(0).messages()).hasSize(1);
        assertThat(completions.get(0).messages().get(0).content()).isEqualTo("olá");
    }

    @Test
    void falhaDoProviderMantemUserSemAssistant() {
        Conversation conversation = persistedConversation(owner);
        failure = new AIProviderFailureException("HTTP_500");

        assertThatThrownBy(() -> sendMessage.send(new SendMessageCommand(
            tenantId, owner, conversation.id(), "olá", modelId, null)))
            .isInstanceOf(AIProviderFailureException.class);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role().name()).isEqualTo("USER");
    }

    @Test
    void conversaArquivadaRejeitaEnvioSemGravarMensagem() {
        Conversation conversation = persistedConversation(owner).archive();

        assertThatThrownBy(() -> sendMessage.send(new SendMessageCommand(
            tenantId, owner, conversation.id(), "olá", modelId, null)))
            .isInstanceOf(ConversationArchivedException.class);

        assertThat(messages).isEmpty();
        assertThat(completions).isEmpty();
    }

    @Test
    void conversaDeOutraPessoaRetornaNotFound() {
        Conversation conversation = persistedConversation(owner);

        assertThatThrownBy(() -> sendMessage.send(new SendMessageCommand(
            tenantId, UserId.random(), conversation.id(), "olá", modelId, null)))
            .isInstanceOf(ConversationNotFoundException.class);
        assertThat(messages).isEmpty();

        assertThatThrownBy(() -> listMessages.list(tenantId, UserId.random(), conversation.id()))
            .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void historicoEhLimitadoAsUltimasVinteMensagens() {
        Conversation conversation = persistedConversation(owner);
        for (int i = 0; i < 25; i++) {
            messages.add(ChatMessage.user(UUID.randomUUID(), tenantId, conversation.id(),
                "m" + i));
        }

        exchange.exchange(conversation, "nova", null);

        assertThat(completions.get(0).messages()).hasSize(20);
    }

    @Test
    void listagemNormalizaPaginaETamanho() {
        List<int[]> captured = new ArrayList<>();
        ConversationRepository capturing = new ConversationRepository() {
            @Override
            public Conversation save(Conversation conversation) {
                return conversation;
            }

            @Override
            public Optional<Conversation> findByTenantIdAndIdAndOwner(TenantId tid, UUID id,
                                                                      UserId actor) {
                return Optional.empty();
            }

            @Override
            public PageResult<Conversation> findByOwner(TenantId tid, UserId actor, int page,
                                                        int size) {
                captured.add(new int[]{page, size});
                return new PageResult<>(List.of(), 0, page, size);
            }
        };

        ListConversations list = new ListConversations(capturing);
        list.list(tenantId, owner, -3, 500);
        list.list(tenantId, owner, 0, 0);

        assertThat(captured.get(0)).containsExactly(0, 100);
        assertThat(captured.get(1)).containsExactly(0, 20);
    }

    private Conversation persistedConversation(UserId actor) {
        Conversation conversation = Conversation.start(UUID.randomUUID(), tenantId, actor, "olá");
        conversations.put(conversation.id(), conversation);
        return conversation;
    }
}
