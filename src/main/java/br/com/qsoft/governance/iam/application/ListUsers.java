package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserSearchFilter;
import br.com.qsoft.governance.iam.application.port.UserSearchRepository;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de listagem/busca paginada de usuários do tenant (SPI-62).
 * <p>
 * A AUTORIZAÇÃO (permissão granular USER:READ) é aplicada no endpoint pelo
 * {@code PermissionInterceptor} via {@code @RequiresPermission}. O tenant vem
 * SEMPRE do contexto autenticado — nunca do cliente (ADR-03).
 */
@Service
public class ListUsers {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final UserSearchRepository userSearchRepository;

    public ListUsers(UserSearchRepository userSearchRepository) {
        this.userSearchRepository = userSearchRepository;
    }

    public PageResult<User> list(ListUsersCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        int page = Math.max(command.page(), 0);
        int size = command.size() <= 0 ? DEFAULT_SIZE : Math.min(command.size(), MAX_SIZE);

        UserSearchFilter filter = new UserSearchFilter(
            command.hierarchyLevel(),
            command.departmentId(),
            command.name(),
            command.email()
        );
        return userSearchRepository.search(tenantId, filter, page, size);
    }
}
