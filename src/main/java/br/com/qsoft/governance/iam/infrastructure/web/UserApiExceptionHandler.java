package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.LastTenantAdminException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.RoleAssignmentNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz erros do provisionamento de usuário em respostas HTTP sem vazar internals.
 */
@RestControllerAdvice(basePackageClasses = { UserController.class, RoleController.class })
public class UserApiExceptionHandler {

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailExists(UserEmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleMissingContext(IllegalStateException ex) {
        // TenantContextHolder.require() sem contexto autenticado.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Não autenticado."));
    }

    @ExceptionHandler({ TenantNotFoundException.class, UserNotFoundException.class,
        RoleAssignmentNotFoundException.class })
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({ RoleAssignmentAlreadyExistsException.class, LastTenantAdminException.class })
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /**
     * Corrida em regra de unicidade (ex.: duas concessões idênticas simultâneas,
     * barradas pelo índice parcial): vira 409, sem revelar a constraint.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Operação em conflito com o estado atual dos dados."));
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
