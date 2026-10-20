package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.chat.application.ListConversations;
import br.com.qsoft.governance.chat.application.ListMessages;
import br.com.qsoft.governance.chat.application.SendMessage;
import br.com.qsoft.governance.chat.application.SendMessageCommand;
import br.com.qsoft.governance.chat.application.StartConversation;
import br.com.qsoft.governance.chat.application.StartConversationCommand;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ChatConversationController(StartConversation startConversation,
                                      SendMessage sendMessage,
                                      ListConversations listConversations,
                                      ListMessages listMessages) {
        this.startConversation = startConversation;
        this.sendMessage = sendMessage;
        this.listConversations = listConversations;
        this.listMessages = listMessages;
    }

    @PostMapping
    @RequiresPermission(resource = Resource.CHAT, action = Action.CREATE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ChatTurnResponse> start(@Valid @RequestBody SendMessageRequest request) {
        TenantContext context = TenantContextHolder.require();
        ChatTurnResponse response = ChatTurnResponse.from(startConversation.start(
            new StartConversationCommand(context.tenantId(), context.userId(),
                request.mensagem(), request.requestedModelId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @RequiresPermission(resource = Resource.CHAT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ConversationPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(ConversationPageResponse.from(
            listConversations.list(context.tenantId(), context.userId(), page, size)));
    }

    @GetMapping("/{id}/mensagens")
    @RequiresPermission(resource = Resource.CHAT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<List<MessageResponse>> messages(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(listMessages.list(context.tenantId(), context.userId(), id)
            .stream().map(MessageResponse::from).toList());
    }

    @PostMapping("/{id}/mensagens")
    @RequiresPermission(resource = Resource.CHAT, action = Action.CREATE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<ChatTurnResponse> send(@PathVariable UUID id,
                                                 @Valid @RequestBody SendMessageRequest request) {
        TenantContext context = TenantContextHolder.require();
        ChatTurnResponse response = ChatTurnResponse.from(sendMessage.send(
            new SendMessageCommand(context.tenantId(), context.userId(), id,
                request.mensagem(), request.requestedModelId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
