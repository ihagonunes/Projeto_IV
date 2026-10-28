package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Contrato público do módulo IAM para consulta de identidade/contexto de usuário.
 * Outros módulos consultam a hierarquia de um usuário aqui, em vez de
 * reinterpretar regras de nível (ADR-04).
 */
public interface IdentityQuery {

    Optional<UserContext> getUserContext(UserId userId);

    /**
     * Usuários elegíveis para indicadores de engajamento: ativos do tenant,
     * excluindo quem tem apenas {@code TENANT_ADMIN} como concessão ativa
     * (ADR-40 §3).
     *
     * <p>Default para não quebrar implementações/lambdas existentes; o adapter
     * de produção sobrescreve.
     */
    default List<UserContext> eligibleUsers(TenantId tenantId) {
        throw new UnsupportedOperationException("eligibleUsers não implementado");
    }
}
