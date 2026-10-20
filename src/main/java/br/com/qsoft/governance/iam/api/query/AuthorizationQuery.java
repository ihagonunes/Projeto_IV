package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;

/**
 * Contrato público do módulo IAM para autorização determinística (ADR-04/ADR-36).
 * Outros módulos consultam este contrato em vez de interpretar hierarquia,
 * papéis ou permissões por conta própria.
 */
public interface AuthorizationQuery {

    /**
     * Decisão fina: o ator possui uma concessão que cobre o alvo para a
     * permissão (recurso × ação)? Aplica o teto por recurso (CHAT = SELF).
     */
    boolean isAllowed(TenantId tenantId, UserId actor, Resource resource, Action action,
                      AccessTarget target);

    /**
     * Escopo de visibilidade para consultas agregadas: união das concessões
     * ativas que contêm a permissão. Sem concessão aplicável, {@code None}.
     */
    VisibilityScope visibilityFor(TenantId tenantId, UserId actor, Resource resource, Action action);
}
