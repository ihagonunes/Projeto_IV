package br.com.qsoft.governance.department.domain;

/**
 * Estado de um departamento. INACTIVE representa remoção lógica (o registro é
 * preservado para não deixar referências órfãs em {@code app_user.department_id}).
 */
public enum DepartmentStatus {
    ACTIVE,
    INACTIVE
}
