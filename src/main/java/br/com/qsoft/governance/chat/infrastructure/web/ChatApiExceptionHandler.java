package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.chat.domain.ConversationArchivedException;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
import br.com.qsoft.governance.chat.domain.TurnInProgressOrFailedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz erros do chat e da completion em HTTP, sem vazar detalhes internos. */
@RestControllerAdvice(basePackageClasses = ChatConversationController.class)
public class ChatApiExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ConversationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ConversationArchivedException.class)
    public ResponseEntity<Map<String, String>> handleArchived(ConversationArchivedException ex) {
        return error(HttpStatus.CONFLICT, "CONVERSATION_ARCHIVED", ex.getMessage());
    }

    @ExceptionHandler(TurnInProgressOrFailedException.class)
    public ResponseEntity<Map<String, Object>> handleTurnInProgress(
            TurnInProgressOrFailedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("code", "TURN_IN_PROGRESS_OR_FAILED");
        body.put("messageId", ex.messageId());
        body.put("requestId", ex.requestId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ModelNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleNotAllowed(ModelNotAllowedException ex) {
        return error(HttpStatus.FORBIDDEN, "MODEL_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(NoModelConfiguredException.class)
    public ResponseEntity<Map<String, String>> handleNoModel(NoModelConfiguredException ex) {
        return error(HttpStatus.CONFLICT, "NO_MODEL_CONFIGURED", ex.getMessage());
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleModelUnavailable(ModelUnavailableException ex) {
        return error(HttpStatus.CONFLICT, "MODEL_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(ProviderInactiveException.class)
    public ResponseEntity<Map<String, String>> handleProviderInactive(ProviderInactiveException ex) {
        return error(HttpStatus.CONFLICT, "NO_MODEL_CONFIGURED", ex.getMessage());
    }

    @ExceptionHandler(AIProviderTimeoutException.class)
    public ResponseEntity<Map<String, String>> handleTimeout(AIProviderTimeoutException ex) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT", ex.getMessage());
    }

    @ExceptionHandler(AIProviderFailureException.class)
    public ResponseEntity<Map<String, String>> handleFailure(AIProviderFailureException ex) {
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_FAILURE", ex.getMessage());
    }

    @ExceptionHandler(AIAttemptNotRecordedException.class)
    public ResponseEntity<Map<String, String>> handleAttemptNotRecorded(
            AIAttemptNotRecordedException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ATTEMPT_NOT_RECORDED", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleMissingContext(IllegalStateException ex) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Não autenticado.");
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, IllegalArgumentException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException validation) {
            message = validation.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Dados inválidos.");
        } else {
            message = ex.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = "Dados inválidos.";
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code,
                                                             String message) {
        return ResponseEntity.status(status).body(Map.of("message", message, "code", code));
    }
}
