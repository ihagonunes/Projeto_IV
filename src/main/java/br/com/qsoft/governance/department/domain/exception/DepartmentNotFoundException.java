package br.com.qsoft.governance.department.domain.exception;

import java.util.UUID;

/**
 * Lançada quando um departamento não é encontrado <b>no tenant do contexto</b>.
 * Um departamento de outro tenant produz o mesmo erro (não revela existência
 * cross-tenant).
 */
public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(UUID departmentId) {
        super("Departamento não encontrado: " + departmentId + ".");
    }
}
