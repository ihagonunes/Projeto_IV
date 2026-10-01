package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.domain.exception.DepartmentNameAlreadyExistsException;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
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

class DepartmentApiExceptionHandlerTest {

    private DepartmentApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DepartmentApiExceptionHandler();
    }

    @Test
    void deve_tratar_department_not_found_exception() {
        UUID deptId = UUID.randomUUID();
        DepartmentNotFoundException ex = new DepartmentNotFoundException(deptId);
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_department_name_already_exists_exception() {
        DepartmentNameAlreadyExistsException ex = new DepartmentNameAlreadyExistsException("Engenharia");
        ResponseEntity<Map<String, String>> response = handler.handleNameAlreadyExists(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    void deve_tratar_illegal_state_exception_como_unauthorized() {
        IllegalStateException ex = new IllegalStateException("Sem contexto");
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
