package br.com.qsoft.governance.department.domain.exception;

/**
 * Lançada quando já existe um departamento com o mesmo nome <b>no tenant</b>
 * (a unicidade é por tenant: {@code UNIQUE (tenant_id, name)}).
 */
public class DepartmentNameAlreadyExistsException extends RuntimeException {

    public DepartmentNameAlreadyExistsException(String name) {
        super("Já existe um departamento com o nome '" + name + "' neste tenant.");
    }
}
