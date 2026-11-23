package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.application.AgentExecutionNotFoundException;
import br.com.qsoft.governance.agents.application.AgentFlowAccessDeniedException;
import br.com.qsoft.governance.agents.application.AgentFlowNotFoundException;
import br.com.qsoft.governance.agents.application.AgentFlowNotValidatedException;
import br.com.qsoft.governance.agents.application.AgentFlowPreconditionException;
import br.com.qsoft.governance.agents.application.AgentFlowStateConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros de fluxo de agentes sem vazar detalhes internos. */
@RestControllerAdvice(basePackageClasses = AgentFlowController.class)
public class AgentFlowApiExceptionHandler {

    @ExceptionHandler(AgentFlowNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AgentFlowNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_FLOW_NOT_FOUND"));
    }

    @ExceptionHandler(AgentExecutionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleExecutionNotFound(
            AgentExecutionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_EXECUTION_NOT_FOUND"));
    }

    @ExceptionHandler(AgentFlowAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(AgentFlowAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_FLOW_ACCESS_DENIED"));
    }

    @ExceptionHandler(AgentFlowPreconditionException.class)
    public ResponseEntity<Map<String, String>> handlePrecondition(
            AgentFlowPreconditionException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_FLOW_PRECONDITION_FAILED"));
    }

    @ExceptionHandler(AgentFlowStateConflictException.class)
    public ResponseEntity<Map<String, String>> handleStateConflict(
            AgentFlowStateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_FLOW_STATE_CONFLICT"));
    }

    @ExceptionHandler(AgentFlowNotValidatedException.class)
    public ResponseEntity<Map<String, String>> handleNotValidated(
            AgentFlowNotValidatedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
            .body(Map.of("message", ex.getMessage(), "code", "AGENT_FLOW_NOT_VALIDATED"));
    }
}
