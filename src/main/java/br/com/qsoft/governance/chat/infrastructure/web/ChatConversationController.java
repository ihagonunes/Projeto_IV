package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.model.AllowedModels;
import br.com.qsoft.governance.aigateway.api.query.ModelResolutionQuery;
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
import br.com.qsoft.governance.chat.domain.ConversationStatus;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend do chat privado (ADR-39). O dono é sempre o ator do contexto; o gate
 * {@code BY_TARGET} só exige alguma concessão CHAT, e o caso de uso aplica o
 * teto SELF (conversa de outra pessoa → 404).
 */
@RestController
@RequestMapping("/api/v1/conversas")
public class ChatConversationController {

    private final StartConversation startConversation;
    private final SendMessage sendMessage;
    private final ListConversations listConversations;
    private final ListMessages listMessages;
    private final UpdateConversation updateConversation;
    private final DeleteConversation deleteConversation;
    private final ModelResolutionQuery modelResolutionQuery;

    public ChatConversationController(StartConversation startConversation,
                                      SendMessage sendMessage,
                                      ListConversations listConversations,
                                      ListMessages listMessages,
                                      UpdateConversation updateConversation,
                                      DeleteConversation deleteConversation,
                                      ModelResolutionQuery modelResolutionQuery) {
        this.startConversation = startConversation;
        this.sendMessage = sendMessage;
        this.listConversations = listConversations;
        this.listMessages = listMessages;
        this.updateConversation = updateConversation;
        this.deleteConversation = deleteConversation;
        this.modelResolutionQuery = modelResolutionQuery;
    }

    @PostMapping
    @RequiresPermission(resource = Resource.CHAT, action = Action.CREATE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ChatTurnResponse> start(@Valid @RequestBody SendMessageRequest request) {
        TenantContext context = TenantContextHolder.require();
        ChatTurn turn = startConversation.start(
            new StartConversationCommand(context.tenantId(), context.userId(),
                request.mensagem(), request.requestedModelId(), request.clientMessageId()));
        return ResponseEntity.status(turn.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .body(ChatTurnResponse.from(turn));
    }

    @GetMapping
    @RequiresPermission(resource = Resource.CHAT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ConversationPageResponse> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(ConversationPageResponse.from(
            listConversations.list(context.tenantId(), context.userId(),
                parseListStatus(status), q, page, size)));
    }

    @GetMapping("/modelos")
    @RequiresPermission(resource = Resource.CHAT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AllowedModels> models() {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(modelResolutionQuery.allowedModels(
            context.tenantId(), context.userId()));
    }

    @PatchMapping("/{id}")
    @RequiresPermission(resource = Resource.CHAT, action = Action.CREATE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ConversationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequest request) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(ConversationResponse.from(updateConversation.update(
            new UpdateConversationCommand(context.tenantId(), context.userId(), id,
                request.titulo(), parseUpdateStatus(request.status())))));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = Resource.CHAT, action = Action.CREATE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        deleteConversation.delete(context.tenantId(), context.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/mensagens")
    @RequiresPermission(resource = Resource.CHAT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<List<MessageResponse>> messages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(listMessages.list(context.tenantId(), context.userId(), id,
                page, size)
            .stream()
            .map(view -> MessageResponse.from(view.message(), view.receipt()))
            .toList());
    }

    @PostMapping("/{id}/mensagens")
    @RequiresPermission(resource = Resource.CHAT, action = Action.CREATE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ChatTurnResponse> send(@PathVariable UUID id,
                                                 @Valid @RequestBody SendMessageRequest request) {
        TenantContext context = TenantContextHolder.require();
        ChatTurn turn = sendMessage.send(
            new SendMessageCommand(context.tenantId(), context.userId(), id,
                request.mensagem(), request.requestedModelId(), request.clientMessageId()));
        return ResponseEntity.status(turn.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .body(ChatTurnResponse.from(turn));
    }

    private static ConversationStatus parseListStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status inválido.");
        }
        ConversationStatus parsed;
        try {
            parsed = ConversationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status inválido.");
        }
        if (parsed == ConversationStatus.DELETED) {
            throw new IllegalArgumentException("status inválido.");
        }
        return parsed;
    }

    private static String parseUpdateStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals(ConversationStatus.ACTIVE.name())
            && !normalized.equals(ConversationStatus.ARCHIVED.name())) {
            throw new IllegalArgumentException("status inválido.");
        }
        return normalized;
    }
}
