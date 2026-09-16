package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.AcceptInvite;
import br.com.qsoft.governance.iam.application.AcceptInviteCommand;
import br.com.qsoft.governance.iam.application.CreateInvite;
import br.com.qsoft.governance.iam.application.CreateInviteCommand;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.rbac.Action;
import br.com.qsoft.governance.iam.domain.rbac.Resource;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de convites.
 * - POST /api/v1/convites: emite convite (requer autenticação + INVITE:MANAGE).
 * - POST /api/v1/convites/aceitar: aceita o convite e define a senha (público).
 */
@RestController
@RequestMapping("/api/v1/convites")
public class InviteController {

    private final CreateInvite createInvite;
    private final AcceptInvite acceptInvite;

    public InviteController(CreateInvite createInvite, AcceptInvite acceptInvite) {
        this.createInvite = createInvite;
        this.acceptInvite = acceptInvite;
    }

    @PostMapping
    @RequiresPermission(resource = Resource.INVITE, action = Action.MANAGE)
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request) {
        TenantContext context = TenantContextHolder.require();
        Invite invite = createInvite.create(new CreateInviteCommand(
            context.tenantId(),
            request.email(),
            request.hierarchyLevel(),
            request.departmentId()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(invite));
    }

    @PostMapping("/aceitar")
    public ResponseEntity<AcceptInviteResponse> accept(@Valid @RequestBody AcceptInviteRequest request) {
        User user = acceptInvite.accept(new AcceptInviteCommand(
            request.token(), request.name(), request.password()));
        return ResponseEntity.ok(AcceptInviteResponse.from(user));
    }
}
