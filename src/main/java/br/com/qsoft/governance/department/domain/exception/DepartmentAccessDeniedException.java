package br.com.qsoft.governance.department.domain.exception;

/**
 * Departamento fora do escopo do ator (ADR-36). Deve responder 403.
 */
public class DepartmentAccessDeniedException extends RuntimeException {

    public DepartmentAccessDeniedException() {
        super("Departamento fora do escopo do ator.");
    }
}
