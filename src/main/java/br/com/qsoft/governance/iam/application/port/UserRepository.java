package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída do módulo IAM para persistência de User.
 */
public interface UserRepository {

    User save(User user);

    /**
     * Busca por id <b>restrita ao tenant do contexto autenticado</b> (ADR-03).
     * O tenant vem SEMPRE do {@code TenantContext} — nunca do cliente. Chamadas
     * no caminho de autenticação (sem contexto) devem usar
     * {@link #findByIdUnscoped(UserId)} explicitamente.
     */
    Optional<User> findById(UserId id);

    /**
     * Busca por id <b>sem restrição de tenant</b> — uso <b>exclusivo</b> do
     * caminho de autenticação ({@code TenantContextFilter}), onde o contexto
     * ainda não foi estabelecido. Não usar em regras de negócio.
     * <p>
     * O default delega para {@link #findById(UserId)} por conveniência das
     * implementações em memória (testes); o adapter de produção sobrescreve.
     */
    default Optional<User> findByIdUnscoped(UserId id) {
        return findById(id);
    }

    /**
     * Busca por (tenant, id) com filtro explícito de tenant, sem depender do
     * {@code TenantContext} (útil no caminho público do aceite de convite, que
     * revalida a autoridade do convidante). O default delega para
     * {@link #findById(UserId)} nas implementações em memória (testes).
     */
    default Optional<User> findByTenantIdAndId(TenantId tenantId, UserId id) {
        return findById(id);
    }

    Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email);

    boolean existsByTenantIdAndEmail(TenantId tenantId, String email);

    /**
     * Lista os usuários {@code ACTIVE} do tenant, com filtro explícito por
     * {@code tenant_id} (ADR-03). Default falha alto para implementações em
     * memória que não precisam da listagem.
     */
    default List<User> findActiveByTenantId(TenantId tenantId) {
        throw new UnsupportedOperationException("findActiveByTenantId não implementado");
    }
}
