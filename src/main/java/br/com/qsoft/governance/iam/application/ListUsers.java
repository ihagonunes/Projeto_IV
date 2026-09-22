package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListUsers {

    private final UserRepository userRepository;

    public ListUsers(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> list(ListUsersQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Query é obrigatória.");
        }
        TenantId tenantId = Objects.requireNonNull(query.tenantId(), "tenantId");

        List<User> users = userRepository.findAllByTenantId(tenantId);

        if (query.status() != null && !query.status().isBlank() && !"TODOS".equalsIgnoreCase(query.status())) {
            try {
                UserStatus filterStatus = UserStatus.valueOf(query.status().toUpperCase());
                users = users.stream().filter(u -> u.status() == filterStatus).toList();
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (query.search() != null && !query.search().isBlank()) {
            String term = query.search().toLowerCase().trim();
            users = users.stream()
                .filter(u -> u.name().toLowerCase().contains(term) || u.email().toLowerCase().contains(term))
                .toList();
        }

        return users;
    }
}
