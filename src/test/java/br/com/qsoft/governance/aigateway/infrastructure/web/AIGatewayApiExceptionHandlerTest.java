package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptNotRecordedException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderFailureException;
import br.com.qsoft.governance.aigateway.api.exception.AIProviderTimeoutException;
import br.com.qsoft.governance.aigateway.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.api.exception.ModelNotAllowedException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.api.exception.ModelUnavailableException;
import br.com.qsoft.governance.aigateway.api.exception.NoModelConfiguredException;
import br.com.qsoft.governance.aigateway.api.exception.ProviderInactiveException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.exception.ProviderNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AIGatewayApiExceptionHandlerTest {

    private AIGatewayApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AIGatewayApiExceptionHandler();
    }

    @Test
    void deve_tratar_provider_not_found_exception() {
        ProviderNotFoundException ex = new ProviderNotFoundException(UUID.randomUUID());
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_model_not_found_exception() {
        ModelNotFoundException ex = new ModelNotFoundException(UUID.randomUUID());
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_department_not_found_exception() {
        DepartmentNotFoundException ex = new DepartmentNotFoundException(UUID.randomUUID());
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_provider_name_already_exists_exception() {
        ProviderNameAlreadyExistsException ex = new ProviderNameAlreadyExistsException("OpenAI");
        ResponseEntity<Map<String, String>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_model_name_already_exists_exception() {
        ModelNameAlreadyExistsException ex = new ModelNameAlreadyExistsException("gpt-4o");
        ResponseEntity<Map<String, String>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_model_not_allowed_exception() {
        ModelNotAllowedException ex = new ModelNotAllowedException();
        ResponseEntity<Map<String, String>> response = handler.handleNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_no_model_configured_exception() {
        NoModelConfiguredException ex = new NoModelConfiguredException();
        ResponseEntity<Map<String, String>> response = handler.handleRouting(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_model_unavailable_exception() {
        ModelUnavailableException ex = new ModelUnavailableException();
        ResponseEntity<Map<String, String>> response = handler.handleRouting(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_provider_inactive_exception() {
        ProviderInactiveException ex = new ProviderInactiveException();
        ResponseEntity<Map<String, String>> response = handler.handleRouting(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_timeout_do_provider_como_gateway_timeout() {
        ResponseEntity<Map<String, String>> response =
            handler.handleProviderTimeout(new AIProviderTimeoutException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void deve_tratar_falha_do_provider_como_bad_gateway() {
        ResponseEntity<Map<String, String>> response =
            handler.handleProviderFailure(new AIProviderFailureException("HTTP_500"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).doesNotContainValue("HTTP_500");
    }

    @Test
    void deve_tratar_tentativa_nao_gravada_como_service_unavailable() {
        ResponseEntity<Map<String, String>> response =
            handler.handleAttemptNotRecorded(new AIAttemptNotRecordedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void deve_tratar_illegal_state_exception_como_unauthorized() {
        IllegalStateException ex = new IllegalStateException("Contexto ausente");
        ResponseEntity<Map<String, String>> response = handler.handleMissingContext(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Não autenticado.");
    }

    @Test
    void deve_tratar_illegal_argument_exception_como_bad_request() {
        IllegalArgumentException ex = new IllegalArgumentException("Nome inválido");
        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Nome inválido");
    }

    @Test
    void deve_tratar_method_argument_not_valid_com_erro_de_campo() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "name é obrigatório"));

        MethodParameter methodParameter = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "name é obrigatório");
    }

    @Test
    void deve_tratar_method_argument_not_valid_sem_erros_de_campo() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");

        MethodParameter methodParameter = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Dados inválidos.");
    }
}
