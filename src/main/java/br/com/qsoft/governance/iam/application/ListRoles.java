package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.GrantAuthority;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.SystemRole;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lista os papéis de sistema com escopo, posto e se o ator pode concedê-los
 * (SPEC-01 §7.5). {@code grantable} usa a autoridade de convite do ator no seu
 * próprio departamento (o mesmo critério da tela de convites).
 */
@Service
public class ListRoles {

    private final UserRepository userRepository;
    private final GrantAuthority grantAuthority;

    public ListRoles(UserRepository userRepository, GrantAuthority grantAuthority) {
        this.userRepository = userRepository;
        this.grantAuthority = grantAuthority;
    }

    public List<RoleSummary> list(TenantId tenantId, UserId actor) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actor, "actor é obrigatório.");
        User user = userRepository.findByTenantIdAndId(tenantId, actor)
            .orElseThrow(() -> new UserNotFoundException(actor));
        UUID departmentId = user.departmentId();
        return Arrays.stream(SystemRole.values())
            .map(role -> new RoleSummary(
                role.name(),
                role.scope().name(),
                role.rank(),
                grantAuthority.canGrantByInvite(tenantId, actor, role, departmentId)))
            .toList();
    }
}
