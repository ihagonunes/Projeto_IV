package br.com.qsoft.governance.aigateway.domain.exception;

import java.util.UUID;

/**
 * Departamento não encontrado no tenant do contexto, na orquestração do
 * aigateway (SPI-82). O aigateway valida a existência pelo contrato público
 * {@code department.api.query.OrganizationQuery} (não acessa o domínio interno).
 */
public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(UUID departmentId) {
        super("Departamento não encontrado: " + departmentId + ".");
    }
}
