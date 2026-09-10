package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.application.AuthenticatedUser;

/**
 * Porta de saída para emissão do token JWT (implementada na infraestrutura).
 */
public interface TokenIssuer {

    String issue(AuthenticatedUser user);
}
