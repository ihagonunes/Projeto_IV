package br.com.qsoft.governance.iam.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class UserApiExceptionHandlerTest {

    private UserApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserApiExceptionHandler();
    }

    @Test
    void deve_tratarEmailExists() {
        var ex = new UserEmailAlreadyExistsException("user@qsoft.com");
        ResponseEntity<Map<String, String>> response = handler.handleEmailExists(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratarAuthorizationDenied() {
        ResponseEntity<Map<String, String>> response = handler.handleAuthorizationDenied(
            new AuthorizationDeniedException("Sem permissão"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Sem permissão");
    }

    @Test
    void deve_tratarMissingContext() {
        ResponseEntity<Map<String, String>> response = handler.handleMissingContext(
            new IllegalStateException("Sem contexto"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Não autenticado.");
    }

    @Test
    void deve_tratarNotFound_UserNotFoundException() {
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(
            new UserNotFoundException(UserId.random()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void deve_tratarNotFound_TenantNotFoundException() {
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(
            new TenantNotFoundException(TenantId.random()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void deve_tratarBadRequest_com_MethodArgumentNotValidException_e_fieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "email é obrigatório");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "email é obrigatório");
    }

    @Test
    void deve_tratarBadRequest_com_MethodArgumentNotValidException_sem_fieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Dados inválidos.");
    }

    @Test
    void deve_tratarBadRequest_com_IllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Erro de validação");

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Erro de validação");
    }
}
