package br.com.qsoft.governance.observability.application;

/** Departamento inexistente no tenant autenticado (resposta 404, sem vazar detalhes). */
public class ProjectionDepartmentNotFoundException extends RuntimeException {

    public ProjectionDepartmentNotFoundException() {
        super("Departamento não encontrado.");
    }
}
