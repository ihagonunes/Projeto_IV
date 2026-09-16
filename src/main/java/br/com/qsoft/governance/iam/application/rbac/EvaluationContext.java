package br.com.qsoft.governance.iam.application.rbac;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import java.util.UUID;

/**
 * Contexto de avaliação de permissão: tenant e usuário autenticados e,
 * opcionalmente, o Department alvo (escopo organizacional).
 */
public record EvaluationContext(TenantId tenantId, UserId userId, UUID departmentId) {

    public EvaluationContext {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(userId, "userId é obrigatório.");
    }

    public static EvaluationContext of(TenantId tenantId, UserId userId) {
        return new EvaluationContext(tenantId, userId, null);
    }
}
