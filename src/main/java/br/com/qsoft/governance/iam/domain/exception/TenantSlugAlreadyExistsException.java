package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando se tenta registrar um tenant com um slug já existente.
 */
public class TenantSlugAlreadyExistsException extends RuntimeException {

    public TenantSlugAlreadyExistsException(String slug) {
        super("Já existe um tenant com o slug '" + slug + "'.");
    }
}
