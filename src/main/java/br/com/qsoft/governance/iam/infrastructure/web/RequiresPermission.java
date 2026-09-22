package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Resource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara a permissão (recurso × ação) exigida para executar um endpoint.
 * O {@link PermissionInterceptor} avalia a permissão via PolicyEvaluator
 * usando o TenantContext autenticado.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    Resource resource();

    Action action();
}
