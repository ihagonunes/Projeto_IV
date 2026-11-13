package br.com.qsoft.governance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.aigateway.api.query.AttemptReceiptQuery;
import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.chat.domain.ConversationStatus;
import br.com.qsoft.governance.chat.domain.MessageRole;
import br.com.qsoft.governance.chat.domain.TurnInProgressOrFailedException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class ChatUseCasesTest {

    private static final String CLIENT_ID = "turn-001";
    private final TenantId tenant = TenantId.random();
    private final UserId owner = UserId.random();
    private final Conversation conversation = Conversation.start(UUID.randomUUID(), tenant, owner, "Pergunta");
    private final ChatMessage user = ChatMessage.user(UUID.randomUUID(), tenant, conversation.id(), "Pergunta", CLIENT_ID);
    private final ChatMessage assistant = ChatMessage.assistant(UUID.randomUUID(), tenant, conversation.id(),
        "Resposta", UUID.randomUUID());
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final AttemptReceiptQuery receipts = mock(AttemptReceiptQuery.class);
    private final ChatMessageWriter writer = mock(ChatMessageWriter.class);
    private final ChatExchange exchange = mock(ChatExchange.class);
    private final ChatTurnReplay replay = new ChatTurnReplay(messages, receipts);
    private final StartConversation start = new StartConversation(writer, exchange, conversations, messages, replay);
    private final SendMessage send = new SendMessage(conversations, messages, exchange, replay);
    private final ListMessages list = new ListMessages(conversations, messages, receipts);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = CLIENT_ID)
    void startsNewConversationWhenThereIsNoPreviousTurn(String clientId) {
        ChatTurn result = new ChatTurn(conversation, user, assistant, null);
        when(exchange.exchange(any(Conversation.class), eq("Nova pergunta"), isNull(), eq(clientId)))
            .thenReturn(result);

        assertThat(start.start(new StartConversationCommand(tenant, owner, "Nova pergunta", null, clientId)))
            .isSameAs(result);

        org.mockito.ArgumentCaptor<Conversation> created = org.mockito.ArgumentCaptor.forClass(Conversation.class);
        verify(writer).createConversation(created.capture());
        assertThat(created.getValue().title()).isEqualTo("Nova pergunta");
        assertThat(created.getValue().tenantId()).isEqualTo(tenant);
        assertThat(created.getValue().ownerUserId()).isEqualTo(owner);
    }

    @Test
    void startReplaysExistingTurnAndUsesDurableReceipt() {
        allowConversation();
        when(messages.findUserByOwnerAndClientMessageId(tenant, owner, CLIENT_ID)).thenReturn(Optional.of(user));
        assistantAfter(assistant);
        ReceiptInfo receipt = receipt(null);
        when(receipts.receipts(tenant, owner, Set.of(assistant.requestId())))
            .thenReturn(Map.of(assistant.requestId(), receipt));

        ChatTurn turn = start.start(new StartConversationCommand(tenant, owner, "Ignorada", null, CLIENT_ID));

        assertThat(turn.replayed()).isTrue();
        assertThat(turn.assistantMessage()).isSameAs(assistant);
        assertThat(turn.completion().modelName()).isEqualTo("Modelo");
        assertThat(turn.completion().latencyMs()).isZero();
        verifyNoInteractions(writer, exchange);
    }

    @Test
    void sendReplaysAnAlreadyProcessedMessage() {
        allowConversation();
        when(messages.findUserByClientMessageId(tenant, conversation.id(), owner, CLIENT_ID))
            .thenReturn(Optional.of(user));
        assistantAfter(assistant);

        ChatTurn turn = send.send(sendCommand(CLIENT_ID));

        assertThat(turn.replayed()).isTrue();
        assertThat(turn.completion()).isNull();
        verifyNoInteractions(exchange);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sendRetriesReadAfterUniqueKeyConflict(boolean stored) {
        allowConversation();
        when(exchange.exchange(conversation, "Pergunta", null, CLIENT_ID))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(messages.findUserByClientMessageId(tenant, conversation.id(), owner, CLIENT_ID))
            .thenReturn(Optional.empty(), stored ? Optional.of(user) : Optional.empty());
        if (stored) {
            assistantAfter(assistant);
            when(receipts.receipts(tenant, owner, Set.of(assistant.requestId())))
                .thenReturn(Map.of(assistant.requestId(), receipt(25L)));
            assertThat(send.send(sendCommand(CLIENT_ID)).completion().latencyMs()).isEqualTo(25);
        } else {
            assertThatThrownBy(() -> send.send(sendCommand(CLIENT_ID)))
                .isInstanceOf(TurnInProgressOrFailedException.class);
        }
    }

    @Test
    void sendPropagatesWriteFailureWhenThereIsNoClientId() {
        allowConversation();
        var failure = new DataIntegrityViolationException("write failed");
        when(exchange.exchange(conversation, "Pergunta", null, null)).thenThrow(failure);

        assertThatThrownBy(() -> send.send(sendCommand(null))).isSameAs(failure);
        verifyNoInteractions(messages, receipts);
    }

    @Test
    void replayRequiresAssistantMessageAndItsRequestId() {
        assertThatThrownBy(() -> replay.replay(conversation, user))
            .isInstanceOfSatisfying(TurnInProgressOrFailedException.class,
                error -> assertThat(error.messageId()).isEqualTo(user.id()));
        assistantAfter(ChatMessage.restore(assistant.id(), tenant, conversation.id(), MessageRole.ASSISTANT,
            "Resposta legada", null, assistant.createdAt()));
        assertThatThrownBy(() -> replay.replay(conversation, user))
            .isInstanceOf(TurnInProgressOrFailedException.class);
        verifyNoInteractions(receipts);
    }

    @Test
    void listMessagesBoundsPaginationAndJoinsReceiptsOnlyToAssistantRows() {
        allowConversation();
        ReceiptInfo receipt = receipt(25L);
        when(messages.findPage(tenant, conversation.id(), owner, 0, 50)).thenReturn(List.of(user, assistant));
        when(receipts.receipts(tenant, owner, Set.of(assistant.requestId())))
            .thenReturn(Map.of(assistant.requestId(), receipt));

        assertThat(list.list(tenant, owner, conversation.id())).containsExactly(
            new ChatMessageView(user, null), new ChatMessageView(assistant, receipt));
        list.list(tenant, owner, conversation.id(), -2, 500);
        verify(messages).findPage(tenant, conversation.id(), owner, 0, 200);
        list.list(tenant, owner, conversation.id(), 1, 0);
        verify(messages).findPage(tenant, conversation.id(), owner, 1, 50);
    }

    @Test
    void missingConversationPreventsMessageReadsAndWrites() {
        assertThatThrownBy(() -> list.list(tenant, owner, conversation.id()))
            .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> send.send(sendCommand(CLIENT_ID)))
            .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> new DeleteConversation(conversations).delete(tenant, owner, conversation.id()))
            .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> new UpdateConversation(conversations).update(updateCommand("Título", null)))
            .isInstanceOf(ConversationNotFoundException.class);
        verifyNoInteractions(messages, receipts, writer, exchange);
    }

    @Test
    void renamesArchivesRestoresAndSoftDeletesOwnedConversation() {
        allowConversation();
        when(conversations.save(conversation)).thenReturn(conversation);
        UpdateConversation update = new UpdateConversation(conversations);

        assertThat(update.update(updateCommand("Renomeada", null)).title()).isEqualTo("Renomeada");
        assertThat(update.update(updateCommand(null, "ARCHIVED")).isArchived()).isTrue();
        assertThat(update.update(updateCommand("Restaurada", "ACTIVE")).status()).isEqualTo(ConversationStatus.ACTIVE);
        new DeleteConversation(conversations).delete(tenant, owner, conversation.id());
        assertThat(conversation.isDeleted()).isTrue();
        assertThat(conversation.deletedAt()).isNotNull();
    }

    @Test
    void rejectsUpdatesWithoutChangesOrWithUnsupportedStatus() {
        allowConversation();
        UpdateConversation update = new UpdateConversation(conversations);
        assertThatThrownBy(() -> update.update(updateCommand(null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> update.update(updateCommand(null, "DELETED"))).isInstanceOf(IllegalArgumentException.class);
        verify(conversations, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void conversationListValidatesFiltersAndAppliesDefaultAndMaximumPageSizes() {
        ListConversations listing = new ListConversations(conversations);
        var page = new PageResult<>(List.of(conversation), 1, 0, 20);
        when(conversations.search(tenant, owner, ConversationStatus.ACTIVE, null, 0, 20)).thenReturn(page);
        assertThat(listing.list(tenant, owner, -1, 0)).isEqualTo(page);
        listing.list(tenant, owner, null, "busca", 2, 500);
        verify(conversations).search(tenant, owner, ConversationStatus.ACTIVE, "busca", 2, 100);
        assertThatThrownBy(() -> listing.list(tenant, owner, ConversationStatus.DELETED, null, 0, 20))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listing.list(tenant, owner, ConversationStatus.ACTIVE, "a".repeat(101), 0, 20))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void allowConversation() {
        when(conversations.findByTenantIdAndIdAndOwner(tenant, conversation.id(), owner))
            .thenReturn(Optional.of(conversation));
    }

    private void assistantAfter(ChatMessage message) {
        when(messages.findAssistantAfter(tenant, conversation.id(), owner, user.createdAt()))
            .thenReturn(Optional.of(message));
    }

    private SendMessageCommand sendCommand(String clientId) {
        return new SendMessageCommand(tenant, owner, conversation.id(), "Pergunta", null, clientId);
    }

    private UpdateConversationCommand updateCommand(String title, String status) {
        return new UpdateConversationCommand(tenant, owner, conversation.id(), title, status);
    }

    private ReceiptInfo receipt(Long latency) {
        return new ReceiptInfo(assistant.requestId(), UUID.randomUUID(), "Modelo", "Provider",
            10L, 20L, null, "INCONCLUSIVE", latency, "SUCCEEDED");
    }
}
