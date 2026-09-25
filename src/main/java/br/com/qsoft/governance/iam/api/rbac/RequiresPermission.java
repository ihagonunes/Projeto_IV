package br.com.qsoft.governance.iam.api.rbac;

import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara a permissão (recurso × ação) exigida para executar um endpoint.
 * <p>
 * Contrato público do IAM (ADR-34): qualquer módulo pode anotar seus endpoints
 * com esta anotação, usando o vocabulário transversal de {@code sharedkernel.rbac}.
 * A avaliação é feita pelo {@code PermissionInterceptor} + {@code PolicyEvaluator}
 * do IAM, com o {@code TenantContext} autenticado — nenhum módulo reimplementa
 * autorização (ADR-04 / ADR-33).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    Resource resource();

    Action action();
}
