package br.com.qsoft.governance.iam.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class TenantApiExceptionHandlerTest {

    private TenantApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TenantApiExceptionHandler();
    }

    @Test
    void deve_tratarSlugAlreadyExists() {
        var ex = new TenantSlugAlreadyExistsException("qsoft");
        ResponseEntity<Map<String, String>> response = handler.handleSlugAlreadyExists(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratarDomainAlreadyExists() {
        var ex = new TenantDomainAlreadyExistsException("qsoft.com");
        ResponseEntity<Map<String, String>> response = handler.handleDomainAlreadyExists(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratarTenantNotFound() {
        TenantId tenantId = TenantId.random();
        ResponseEntity<Map<String, String>> response = handler.handleTenantNotFound(
            new TenantNotFoundException(tenantId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void deve_tratarAuthorizationDenied() {
        ResponseEntity<Map<String, String>> response = handler.handleAuthorizationDenied(
            new AuthorizationDeniedException("Sem acesso"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Sem acesso");
    }

    @Test
    void deve_tratarMissingContext() {
        ResponseEntity<Map<String, String>> response = handler.handleMissingContext(
            new IllegalStateException("Sem contexto"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Não autenticado.");
    }

    @Test
    void deve_tratarBadRequest_com_MethodArgumentNotValidException_e_fieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "slug", "slug é obrigatório");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "slug é obrigatório");
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
        IllegalArgumentException ex = new IllegalArgumentException("Erro de argumento");

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Erro de argumento");
    }
}
