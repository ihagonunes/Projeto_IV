package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.EngagementAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Erros do engajamento sem vazar detalhes internos. */
@RestControllerAdvice(basePackageClasses = EngagementAdoptionController.class)
public class EngagementApiExceptionHandler {

    @ExceptionHandler(EngagementAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(EngagementAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "message", ex.getMessage(),
            "code", "USAGE_ACCESS_DENIED"));
    }

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        String message = ex.getMessage() == null ? "Dados inválidos." : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of(
            "message", message,
            "code", "INVALID_REQUEST"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleMissingContext(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "message", "Não autenticado.",
            "code", "UNAUTHENTICATED"));
    }
}
