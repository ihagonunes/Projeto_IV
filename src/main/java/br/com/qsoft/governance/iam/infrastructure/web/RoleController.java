package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.iam.application.ListRoles;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista os papéis de sistema com escopo, posto e {@code concedivel} para o ator
 * (SPEC-01 §6/§7.5). Requer {@code ROLE:READ}.
 */
@RestController
@RequestMapping("/api/v1/papeis")
public class RoleController {

    private final ListRoles listRoles;

    public RoleController(ListRoles listRoles) {
        this.listRoles = listRoles;
    }

    @GetMapping
    @RequiresPermission(resource = Resource.ROLE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<List<RoleSummaryResponse>> list() {
        TenantContext context = TenantContextHolder.require();
        List<RoleSummaryResponse> roles =
            listRoles.list(context.tenantId(), context.userId()).stream()
                .map(RoleSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(roles);
    }
}
