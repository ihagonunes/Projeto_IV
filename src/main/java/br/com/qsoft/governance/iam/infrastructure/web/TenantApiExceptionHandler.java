package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz exceções do módulo IAM em respostas HTTP, sem vazar stack trace.
 * Escopado aos controllers do módulo IAM.
 */
@RestControllerAdvice(basePackageClasses = TenantController.class)
public class TenantApiExceptionHandler {

    @ExceptionHandler(TenantSlugAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleSlugAlreadyExists(TenantSlugAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getDefaultMessage())
            .orElse("Dados inválidos.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
