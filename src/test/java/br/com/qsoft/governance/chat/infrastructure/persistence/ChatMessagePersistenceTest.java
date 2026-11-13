package br.com.qsoft.governance.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.MessageRole;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class ChatMessagePersistenceTest {

    private static final String CLIENT_MESSAGE_ID = "turn-001";
    private static final Instant STARTED_AT = Instant.parse("2026-07-01T12:00:00Z");

    private final TenantId tenant = TenantId.random();
    private final UserId owner = UserId.random();
    private final UUID conversationId = UUID.randomUUID();
    private final ChatMessage user = ChatMessage.restore(UUID.randomUUID(), tenant, conversationId,
        MessageRole.USER, "Pergunta de teste", null, CLIENT_MESSAGE_ID, STARTED_AT);
    private final ChatMessage assistant = ChatMessage.restore(UUID.randomUUID(), tenant, conversationId,
        MessageRole.ASSISTANT, "Resposta de teste", UUID.randomUUID(), null, STARTED_AT.plusSeconds(1));
    private final ChatMessageJpaRepository jpa = mock(ChatMessageJpaRepository.class);
    private final ChatMessageRepositoryAdapter adapter = new ChatMessageRepositoryAdapter(jpa);
    private final ChatMessageJpaMapper mapper = new ChatMessageJpaMapper();

    @ParameterizedTest
    @EnumSource(MessageRole.class)
    void savesMessageFieldsAndRestoresRoleSpecificIds(MessageRole role) {
        ChatMessage message = role == MessageRole.USER ? user : assistant;
        when(jpa.save(any(ChatMessageJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(adapter.save(message)).usingRecursiveComparison().isEqualTo(message);

        ArgumentCaptor<ChatMessageJpaEntity> captured = ArgumentCaptor.forClass(ChatMessageJpaEntity.class);
        verify(jpa).save(captured.capture());
        ChatMessageJpaEntity entity = captured.getValue();
        assertThat(entity.getId()).isEqualTo(message.id());
        assertThat(entity.getTenantId()).isEqualTo(tenant.value());
        assertThat(entity.getConversationId()).isEqualTo(conversationId);
        assertThat(entity.getRole()).isEqualTo(role);
        assertThat(entity.getContent()).isEqualTo(message.content());
        assertThat(entity.getRequestId()).isEqualTo(message.requestId());
        assertThat(entity.getClientMessageId()).isEqualTo(message.clientMessageId());
        assertThat(entity.getCreatedAt()).isEqualTo(message.createdAt());
    }

    @Test
    void returnsRecentMessagesInChronologicalOrder() {
        when(jpa.findLastByOwner(tenant.value(), conversationId, owner.value(), 2))
            .thenReturn(List.of(mapper.toEntity(assistant), mapper.toEntity(user)));

        assertThat(adapter.findLastMessages(tenant, conversationId, owner, 2))
            .extracting(ChatMessage::id).containsExactly(user.id(), assistant.id());
    }

    @Test
    void listsMessagesByTenantConversationAndOwner() {
        when(jpa.findByOwner(tenant.value(), conversationId, owner.value()))
            .thenReturn(List.of(mapper.toEntity(user), mapper.toEntity(assistant)));
        TenantId otherTenant = TenantId.random();
        UserId otherOwner = UserId.random();

        assertThat(adapter.findByConversation(tenant, conversationId, owner))
            .extracting(ChatMessage::id).containsExactly(user.id(), assistant.id());
        assertThat(adapter.findByConversation(otherTenant, conversationId, owner)).isEmpty();
        assertThat(adapter.findByConversation(tenant, conversationId, otherOwner)).isEmpty();
        verify(jpa).findByOwner(otherTenant.value(), conversationId, owner.value());
        verify(jpa).findByOwner(tenant.value(), conversationId, otherOwner.value());
    }

    @Test
    void translatesPageAndSizeIntoLimitAndOffset() {
        when(jpa.findPageByOwner(tenant.value(), conversationId, owner.value(), 5, 10))
            .thenReturn(List.of(mapper.toEntity(assistant)));

        assertThat(adapter.findPage(tenant, conversationId, owner, 2, 5))
            .extracting(ChatMessage::id).containsExactly(assistant.id());
    }

    @Test
    void findsUserMessageByClientIdWithinConversation() {
        when(jpa.findUserByClientMessageId(tenant.value(), conversationId, owner.value(), CLIENT_MESSAGE_ID))
            .thenReturn(Optional.of(mapper.toEntity(user)));

        assertThat(adapter.findUserByClientMessageId(tenant, conversationId, owner, CLIENT_MESSAGE_ID))
            .hasValueSatisfying(found -> assertThat(found).usingRecursiveComparison().isEqualTo(user));
    }

    @Test
    void findsUserMessageByClientIdAcrossConversationsOfTheSameOwner() {
        when(jpa.findUserByOwnerAndClientMessageId(tenant.value(), owner.value(), CLIENT_MESSAGE_ID))
            .thenReturn(Optional.of(mapper.toEntity(user)));

        assertThat(adapter.findUserByOwnerAndClientMessageId(tenant, owner, CLIENT_MESSAGE_ID))
            .hasValueSatisfying(found -> assertThat(found).usingRecursiveComparison().isEqualTo(user));
    }

    @Test
    void findsAssistantMessageUsingTheGivenTimestamp() {
        when(jpa.findAssistantAfter(tenant.value(), conversationId, owner.value(), STARTED_AT))
            .thenReturn(Optional.of(mapper.toEntity(assistant)));

        assertThat(adapter.findAssistantAfter(tenant, conversationId, owner, STARTED_AT))
            .hasValueSatisfying(found -> assertThat(found).usingRecursiveComparison().isEqualTo(assistant));
    }
}
