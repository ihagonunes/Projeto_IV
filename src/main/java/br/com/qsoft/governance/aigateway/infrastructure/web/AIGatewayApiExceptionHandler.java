package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptAccessDeniedException;
import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelPriceRequiredException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz exceções do módulo {@code aigateway} em respostas HTTP, sem vazar
 * stack trace nem detalhes internos.
 */
@RestControllerAdvice(basePackageClasses = AIGatewayCatalogController.class)
public class AIGatewayApiExceptionHandler {

    @ExceptionHandler({ ProviderNotFoundException.class, ModelNotFoundException.class,
        DepartmentNotFoundException.class })
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({ ProviderNameAlreadyExistsException.class,
        ModelNameAlreadyExistsException.class })
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ModelNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleNotAllowed(ModelNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ModelPriceRequiredException.class)
    public ResponseEntity<Map<String, String>> handlePriceRequired(ModelPriceRequiredException ex) {
        return ResponseEntity.badRequest().body(Map.of(
            "message", ex.getMessage(),
            "code", "MODEL_PRICE_REQUIRED"));
    }

    /** RF047: filtro/tentativa fora do escopo de visibilidade. */
    @ExceptionHandler(AIAttemptAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAttemptAccessDenied(
            AIAttemptAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    /**
     * SPI-85: ausência de modelo permitido/default, modelo indisponível ou
     * provider inativo. O routing é determinístico — não há fallback; a mensagem
     * orienta contatar o administrador.
     */
    @ExceptionHandler({ NoModelConfiguredException.class, ModelUnavailableException.class,
        ProviderInactiveException.class })
    public ResponseEntity<Map<String, String>> handleRouting(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /** Falha técnica do provider (ADR-38): timeout 504, erro 502, tentativa não gravada 503. */
    @ExceptionHandler(AIProviderTimeoutException.class)
    public ResponseEntity<Map<String, String>> handleProviderTimeout(AIProviderTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AIProviderFailureException.class)
    public ResponseEntity<Map<String, String>> handleProviderFailure(AIProviderFailureException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AIAttemptNotRecordedException.class)
    public ResponseEntity<Map<String, String>> handleAttemptNotRecorded(AIAttemptNotRecordedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleMissingContext(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Não autenticado."));
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
