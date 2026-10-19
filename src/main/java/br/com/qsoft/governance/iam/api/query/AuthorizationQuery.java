package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;

/**
 * Contrato público do módulo IAM para autorização determinística.
 * Outros módulos consultam este contrato (ADR-04/ADR-36) em vez de interpretar
 * hierarquia, papéis ou permissões por conta própria.
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

    /**
     * Trilha legada em remoção (ADR-36). Mantida até a TASK-S3-A8 migrar os
     * consumidores restantes.
     */
    @Deprecated(forRemoval = true)
    AuthorizationResult checkPermission(UserId userId, Permission permission);

    /**
     * Trilha legada em remoção (ADR-36). Substituída por {@link #visibilityFor}.
     */
    @Deprecated(forRemoval = true)
    AuthorizationScope resolveScope(UserId userId);
}
