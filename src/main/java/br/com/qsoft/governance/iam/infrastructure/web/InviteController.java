package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.iam.application.AcceptInvite;
import br.com.qsoft.governance.iam.application.AcceptInviteCommand;
import br.com.qsoft.governance.iam.application.CreateInvite;
import br.com.qsoft.governance.iam.application.CreateInviteCommand;
import br.com.qsoft.governance.iam.application.IssuedInvite;
import br.com.qsoft.governance.iam.application.ListInvites;
import br.com.qsoft.governance.iam.application.ResendInvite;
import br.com.qsoft.governance.iam.application.ResendInviteCommand;
import br.com.qsoft.governance.iam.application.RevokeInvite;
import br.com.qsoft.governance.iam.application.RevokeInviteCommand;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de convites.
 * <ul>
 *   <li>{@code POST /api/v1/convites}: emite convite (requer autenticação +
 *       INVITE:MANAGE e a delegação {@code podeConceder}).</li>
 *   <li>{@code POST /api/v1/convites/aceitar}: aceita o convite e define a senha
 *       (público), revalidando a autoridade do convidante.</li>
 * </ul>
 * Os endpoints de gestão declaram {@code BY_TARGET}: o gate aceita qualquer
 * concessão com INVITE:MANAGE e a checagem fina de delegação é feita no caso de uso.
 */
@RestController
@RequestMapping("/api/v1/convites")
public class InviteController {

    private final CreateInvite createInvite;
    private final AcceptInvite acceptInvite;
    private final ResendInvite resendInvite;
    private final RevokeInvite revokeInvite;
    private final ListInvites listInvites;

    public InviteController(CreateInvite createInvite,
                            AcceptInvite acceptInvite,
                            ResendInvite resendInvite,
                            RevokeInvite revokeInvite,
                            ListInvites listInvites) {
        this.createInvite = createInvite;
        this.acceptInvite = acceptInvite;
        this.resendInvite = resendInvite;
        this.revokeInvite = revokeInvite;
        this.listInvites = listInvites;
    }

    /**
     * Lista os convites do tenant do contexto autenticado (sem expor tokens),
     * restritos aos que o ator pode conceder.
     */
    @GetMapping
    @RequiresPermission(resource = Resource.INVITE, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<List<InviteSummaryResponse>> list() {
        TenantContext context = TenantContextHolder.require();
        List<InviteSummaryResponse> invites =
            listInvites.list(context.tenantId(), context.userId()).stream()
                .map(InviteSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(invites);
    }

    @PostMapping
    @RequiresPermission(resource = Resource.INVITE, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request) {
        TenantContext context = TenantContextHolder.require();
        IssuedInvite issued = createInvite.create(new CreateInviteCommand(
            context.tenantId(),
            request.email(),
            request.roleKey(),
            request.departmentId(),
            request.hierarchyLevel(),
            context.userId().value()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(issued));
    }

    @PostMapping("/aceitar")
    public ResponseEntity<AcceptInviteResponse> accept(@Valid @RequestBody AcceptInviteRequest request) {
        User user = acceptInvite.accept(new AcceptInviteCommand(
            request.token(), request.name(), request.password()));
        return ResponseEntity.ok(AcceptInviteResponse.from(user));
    }

    /**
     * Reenvia um convite não aceito gerando um novo token/expiração (SPI-59).
     */
    @PostMapping("/{id}/reenviar")
    @RequiresPermission(resource = Resource.INVITE, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<InviteResponse> resend(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        IssuedInvite issued = resendInvite.resend(
            new ResendInviteCommand(context.tenantId(), InviteId.of(id), context.userId()));
        return ResponseEntity.ok(InviteResponse.from(issued));
    }

    /**
     * Revoga um convite não aceito.
     */
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = Resource.INVITE, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        revokeInvite.revoke(
            new RevokeInviteCommand(context.tenantId(), InviteId.of(id), context.userId()));
        return ResponseEntity.noContent().build();
    }
}
