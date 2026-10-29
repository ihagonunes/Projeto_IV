package br.com.qsoft.governance.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras de domínio do chat: título (60) e conteúdo (1..8000). */
class ChatDomainTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId owner = UserId.random();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void tituloSaoOsPrimeiros60CaracteresDaPrimeiraMensagem() {
        Conversation conversation = Conversation.start(UUID.randomUUID(), tenantId, owner,
            "  Olá   mundo, preciso de ajuda  ");

        assertThat(conversation.title()).isEqualTo("Olá mundo, preciso de ajuda");
        assertThat(conversation.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.ownerUserId()).isEqualTo(owner);
        assertThat(conversation.isOwnedBy(owner)).isTrue();
    }

    @Test
    void tituloEhTruncadoEm60Caracteres() {
        String message = "a".repeat(100);

        Conversation conversation = Conversation.start(UUID.randomUUID(), tenantId, owner, message);

        assertThat(conversation.title()).hasSize(60);
    }

    @Test
    void mensagemVaziaNaoCriaConversa() {
        assertThatThrownBy(() -> Conversation.start(UUID.randomUUID(), tenantId, owner, "   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversaPodeSerArquivadaERestaurada() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Conversation archived = Conversation.restore(id, tenantId, owner, "t",
            ConversationStatus.ACTIVE, now, now).archive();

        assertThat(archived.status()).isEqualTo(ConversationStatus.ARCHIVED);
        assertThat(archived.updatedAt()).isAfterOrEqualTo(now);

        assertThat(archived.restore().status()).isEqualTo(ConversationStatus.ACTIVE);
    }

    @Test
    void conversaExcluidaNaoVoltaNemAceitaNovasOperacoes() {
        Conversation deleted = Conversation.start(UUID.randomUUID(), tenantId, owner, "t").delete();

        assertThat(deleted.status()).isEqualTo(ConversationStatus.DELETED);
        assertThat(deleted.deletedAt()).isNotNull();
        assertThatThrownBy(deleted::restore).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> deleted.rename("novo"))
            .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(deleted::archive).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(deleted::delete).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void renomearValidaTitulo() {
        Conversation conversation = Conversation.start(UUID.randomUUID(), tenantId, owner, "t");

        assertThat(conversation.rename("Novo título").title()).isEqualTo("Novo título");
        assertThatThrownBy(() -> conversation.rename("   "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversation.rename("x".repeat(201)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mensagemAceitaDe1A8000Caracteres() {
        ChatMessage one = ChatMessage.user(UUID.randomUUID(), tenantId, conversationId, "x");
        ChatMessage max = ChatMessage.user(UUID.randomUUID(), tenantId, conversationId,
            "y".repeat(8000));

        assertThat(one.role()).isEqualTo(MessageRole.USER);
        assertThat(one.requestId()).isNull();
        assertThat(max.content()).hasSize(8000);
    }

    @Test
    void mensagemRejeitaVaziaNulaEOAcimaDe8000() {
        assertThatThrownBy(() -> ChatMessage.user(UUID.randomUUID(), tenantId, conversationId, ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMessage.user(UUID.randomUUID(), tenantId, conversationId, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMessage.user(UUID.randomUUID(), tenantId, conversationId,
            "z".repeat(8001)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void respostaExigeRequestIdDoReceipt() {
        assertThatThrownBy(() -> ChatMessage.assistant(UUID.randomUUID(), tenantId, conversationId,
            "resposta", null))
            .isInstanceOf(NullPointerException.class);

        UUID requestId = UUID.randomUUID();
        ChatMessage assistant = ChatMessage.assistant(UUID.randomUUID(), tenantId, conversationId,
            "resposta", requestId);
        assertThat(assistant.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistant.requestId()).isEqualTo(requestId);
    }
}
