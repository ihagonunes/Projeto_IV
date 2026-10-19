package br.com.qsoft.governance.iam.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class InviteApiExceptionHandlerTest {

    private InviteApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InviteApiExceptionHandler();
    }

    @Test
    void deve_tratarInvalidInvite() {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidInvite(
            new InvalidInviteException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void deve_tratarEmailExists() {
        var ex = new UserEmailAlreadyExistsException("user@qsoft.com");
        ResponseEntity<Map<String, String>> response = handler.handleEmailExists(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratarTenantNotFound() {
        ResponseEntity<Map<String, String>> response = handler.handleTenantNotFound(
            new TenantNotFoundException(TenantId.random()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void deve_tratarBadRequest_com_MethodArgumentNotValidException_e_fieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "email inválido");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "email inválido");
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
    void deve_tratarConflitoDeDados_semVazarConstraint() {
        ResponseEntity<Map<String, String>> response = handler.handleDataConflict(
            new DataIntegrityViolationException("uq_app_user_tenant_email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message")).doesNotContain("uq_app_user_tenant_email");
    }

    @Test
    void deve_tratarBadRequest_com_IllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Erro no convite");

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Erro no convite");
    }
}
