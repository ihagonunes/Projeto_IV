package br.com.qsoft.governance.aigateway.api.completion;

/**
 * Porta pública de completion do AI Gateway (ADR-11/ADR-38).
 * <p>
 * É o único caminho para um LLM. Não existe endpoint público genérico de
 * completion. O {@code departmentId} e o {@code requestId} são resolvidos pelo
 * servidor; o chamador não os informa.
 */
public interface AICompletionService {

    CompletionResult complete(CompletionCommand command);
}
