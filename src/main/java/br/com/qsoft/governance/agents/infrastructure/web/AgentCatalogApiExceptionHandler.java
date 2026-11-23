package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentCatalogEntryNotFoundException;
import br.com.qsoft.governance.agents.application.AgentOverrideConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Erros do catálogo de agentes sem expor detalhes internos. */
@RestControllerAdvice(basePackageClasses = AgentCatalogController.class)
public class AgentCatalogApiExceptionHandler {

    @ExceptionHandler(AgentCatalogEntryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            AgentCatalogEntryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_NOT_FOUND"));
    }

    @ExceptionHandler(AgentOverrideConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(AgentOverrideConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_OVERRIDE_CONFLICT"));
    }

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        String message = ex.getMessage() == null ? "Dados inválidos." : ex.getMessage();
        return ResponseEntity.badRequest()
            .body(Map.of("message", message, "code", "INVALID_REQUEST"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Dados inválidos.");
        return ResponseEntity.badRequest()
            .body(Map.of("message", message, "code", "INVALID_REQUEST"));
    }
}
