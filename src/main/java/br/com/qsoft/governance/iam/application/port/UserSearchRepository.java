package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;

/**
 * Porta de LEITURA (query) do módulo IAM para busca paginada e filtrada de
 * usuários do tenant (SPI-62).
 * <p>
 * Separada de {@link UserRepository} (identidade e mutação) para manter o caso
 * de uso de listagem independente das operações de escrita. O tenant é sempre
 * explícito — nunca inferido do cliente (ADR-03).
 */
public interface UserSearchRepository {

    PageResult<User> search(TenantId tenantId, UserSearchFilter filter, int page, int size);
}
