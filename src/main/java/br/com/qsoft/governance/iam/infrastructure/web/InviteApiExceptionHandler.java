package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.InviteAuthorityLostException;
import br.com.qsoft.governance.iam.domain.exception.InviteNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz erros do fluxo de convites em respostas HTTP sem vazar internals.
 */
@RestControllerAdvice(basePackageClasses = InviteController.class)
public class InviteApiExceptionHandler {

    @ExceptionHandler(InvalidInviteException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInvite(InvalidInviteException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    /** Convite inexistente ou de outro tenant (não revela a existência). */
    @ExceptionHandler(InviteNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleInviteNotFound(InviteNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailExists(UserEmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTenantNotFound(TenantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    /** Convite cuja concessão não é permitida ao ator no momento. */
    @ExceptionHandler(InviteAuthorityLostException.class)
    public ResponseEntity<Map<String, String>> handleAuthorityLost(InviteAuthorityLostException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /** Ator sem permissão de conceder o papel/escopo pedido. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    /**
     * Corrida no aceite (dois aceites simultâneos do mesmo convite violam a
     * unicidade de e-mail): vira 409, sem revelar a constraint.
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
