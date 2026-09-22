package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando o domínio de e-mail corporativo informado já está cadastrado
 * por outro tenant (SPI-54). O domínio é único globalmente para que a
 * resolução do tenant no login pelo domínio do e-mail não seja ambígua (ADR-32).
 */
public class TenantDomainAlreadyExistsException extends RuntimeException {

    public TenantDomainAlreadyExistsException(String emailDomain) {
        super("Domínio já cadastrado: " + emailDomain + ".");
    }
}
