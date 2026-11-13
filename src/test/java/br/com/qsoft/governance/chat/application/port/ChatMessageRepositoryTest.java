package br.com.qsoft.governance.chat.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageRepositoryTest {

    @Test
    void defaultsSupportRepositoriesWithoutPaginationOrReplayQueries() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class, CALLS_REAL_METHODS);
        TenantId tenant = TenantId.random();
        UserId owner = UserId.random();
        UUID conversation = UUID.randomUUID();
        ChatMessage message = ChatMessage.user(UUID.randomUUID(), tenant, conversation, "Pergunta");
        doReturn(List.of(message)).when(repository).findByConversation(tenant, conversation, owner);

        assertThat(repository.findPage(tenant, conversation, owner, 1, 10)).containsExactly(message);
        assertThat(repository.findUserByClientMessageId(tenant, conversation, owner, "turn-001")).isEmpty();
        assertThat(repository.findUserByOwnerAndClientMessageId(tenant, owner, "turn-001")).isEmpty();
        assertThat(repository.findAssistantAfter(tenant, conversation, owner, Instant.EPOCH)).isEmpty();
    }
}
