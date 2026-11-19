package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.api.spending.SpendingDeniedException;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Inclui os erros financeiros propagados pelo Gateway ao chat e aos agentes. */
@Order(-10)
@RestControllerAdvice
public class SpendingExceptionHandler {
    @ExceptionHandler(SpendingDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(SpendingDeniedException ex) {
        int status = switch (ex.code()) {
            case "SPENDING_FORBIDDEN" -> 403;
            case "SPENDING_NOT_FOUND" -> 404;
            default -> 409;
        };
        return ResponseEntity.status(status).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
}
