package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Contrato público do módulo IAM para autorização determinística.
 * Outros módulos consultam este contrato (ADR-04) em vez de interpretar
 * hierarquia ou permissões por conta própria.
 */
public interface AuthorizationQuery {

    AuthorizationResult checkPermission(UserId userId, Permission permission);

    AuthorizationScope resolveScope(UserId userId);
}
