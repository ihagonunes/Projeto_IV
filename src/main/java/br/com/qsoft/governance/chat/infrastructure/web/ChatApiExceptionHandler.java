package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.chat.domain.ConversationNotFoundException;
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
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ModelNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleNotAllowed(ModelNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({ NoModelConfiguredException.class, ModelUnavailableException.class,
        ProviderInactiveException.class })
    public ResponseEntity<Map<String, String>> handleRouting(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AIProviderTimeoutException.class)
    public ResponseEntity<Map<String, String>> handleTimeout(AIProviderTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AIProviderFailureException.class)
    public ResponseEntity<Map<String, String>> handleFailure(AIProviderFailureException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AIAttemptNotRecordedException.class)
    public ResponseEntity<Map<String, String>> handleAttemptNotRecorded(
            AIAttemptNotRecordedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleMissingContext(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("message", "Não autenticado."));
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
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
