package br.com.qsoft.governance.agents.domain;

/**
 * Resultado da validação server-side de uma versão de fluxo (ADR-44).
 * Publicar e executar exigem {@link #VALID}; rascunho pode ser salvo
 * {@link #INVALID}.
 */
public enum AgentValidationStatus {
    VALID,
    INVALID
}
