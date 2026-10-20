package br.com.qsoft.governance.agentpolicies.infrastructure.web;

import java.util.UUID;

/** Trecho citado na resposta do agente. */
public record PolicySourceResponse(
    UUID documentId,
    String titulo,
    int versao,
    String trecho
) {
}
