package br.com.qsoft.governance.aigateway.application.port;

/**
 * Resolve o segredo do provider a partir da referência (nome de variável de
 * ambiente). O segredo nunca é persistido, logado ou exposto (ADR-10).
 */
public interface CredentialResolver {

    String resolve(String credentialRef);
}
