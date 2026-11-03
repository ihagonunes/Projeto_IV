package br.com.qsoft.governance.chat.infrastructure.persistence;

import br.com.qsoft.governance.chat.application.port.ChatMessageRepository;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter de mensagens; o join garante tenant + dono da conversa. */
@Component
public class ChatMessageRepositoryAdapter implements ChatMessageRepository {

    private final ChatMessageJpaRepository jpaRepository;
    private final ChatMessageJpaMapper mapper = new ChatMessageJpaMapper();

    public ChatMessageRepositoryAdapter(ChatMessageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ChatMessage save(ChatMessage message) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(message)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findLastMessages(TenantId tenantId, UUID conversationId, UserId owner,
                                              int limit) {
        List<ChatMessageJpaEntity> entities = jpaRepository.findLastByOwner(
            tenantId.value(), conversationId, owner.value(), limit);
        List<ChatMessage> messages = new ArrayList<>(entities.stream().map(mapper::toDomain).toList());
        Collections.reverse(messages);
        return messages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findByConversation(TenantId tenantId, UUID conversationId, UserId owner) {
        return jpaRepository.findByOwner(tenantId.value(), conversationId, owner.value()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findPage(TenantId tenantId, UUID conversationId, UserId owner,
                                      int page, int size) {
        return jpaRepository.findPageByOwner(tenantId.value(), conversationId, owner.value(),
                size, page * size).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatMessage> findUserByClientMessageId(TenantId tenantId, UUID conversationId,
                                                           UserId owner, String clientMessageId) {
        return jpaRepository.findUserByClientMessageId(tenantId.value(), conversationId,
            owner.value(), clientMessageId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatMessage> findUserByOwnerAndClientMessageId(TenantId tenantId, UserId owner,
                                                                   String clientMessageId) {
        return jpaRepository.findUserByOwnerAndClientMessageId(tenantId.value(), owner.value(),
            clientMessageId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatMessage> findAssistantAfter(TenantId tenantId, UUID conversationId,
                                                    UserId owner, Instant after) {
        return jpaRepository.findAssistantAfter(tenantId.value(), conversationId, owner.value(),
            after).map(mapper::toDomain);
    }
}
