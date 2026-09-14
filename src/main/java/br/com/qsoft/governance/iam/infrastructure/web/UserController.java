package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de provisionamento de usuário (onboarding/bootstrap).
 * EXPOSIÇÃO PÚBLICA TEMPORÁRIA: o onboarding ainda não tem autenticação.
 * Na implementação completa, este recurso deve exigir JWT + permissão.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UserController {

    private final ProvisionUser provisionUser;

    public UserController(ProvisionUser provisionUser) {
        this.provisionUser = provisionUser;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        User user = provisionUser.provision(new ProvisionUserCommand(
            TenantId.of(request.tenantId()),
            request.email(),
            request.name(),
            request.hierarchyLevel(),
            request.password(),
            request.departmentId(),
            request.permissions()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }
}
