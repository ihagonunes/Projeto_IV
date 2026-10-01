package br.com.qsoft.governance.observability.infrastructure.web;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityApiExceptionHandlerTest {

    private ObservabilityApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ObservabilityApiExceptionHandler();
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
        IllegalArgumentException ex = new IllegalArgumentException("Período inválido");
        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Período inválido");
    }
}
