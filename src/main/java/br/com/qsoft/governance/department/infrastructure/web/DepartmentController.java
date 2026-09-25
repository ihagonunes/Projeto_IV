package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.application.CreateDepartment;
import br.com.qsoft.governance.department.application.CreateDepartmentCommand;
import br.com.qsoft.governance.department.application.DeleteDepartment;
import br.com.qsoft.governance.department.application.GetDepartment;
import br.com.qsoft.governance.department.application.ListDepartments;
import br.com.qsoft.governance.department.application.UpdateDepartment;
import br.com.qsoft.governance.department.application.UpdateDepartmentCommand;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de departamentos do tenant autenticado.
 * <p>
 * O tenant vem SEMPRE do contexto autenticado — nunca do cliente (ADR-03). A
 * autorização usa o contrato público {@code @RequiresPermission} (ADR-34),
 * avaliado pelo {@code PermissionInterceptor} do IAM (ADR-04/ADR-33).
 */
@RestController
@RequestMapping("/api/v1/departamentos")
public class DepartmentController {

    private final CreateDepartment createDepartment;
    private final UpdateDepartment updateDepartment;
    private final DeleteDepartment deleteDepartment;
    private final GetDepartment getDepartment;
    private final ListDepartments listDepartments;

    public DepartmentController(CreateDepartment createDepartment,
                                UpdateDepartment updateDepartment,
                                DeleteDepartment deleteDepartment,
                                GetDepartment getDepartment,
                                ListDepartments listDepartments) {
        this.createDepartment = createDepartment;
        this.updateDepartment = updateDepartment;
        this.deleteDepartment = deleteDepartment;
        this.getDepartment = getDepartment;
        this.listDepartments = listDepartments;
    }

    /** Lista departamentos do tenant (por padrão, apenas ACTIVE). Requer DEPARTMENT:READ. */
    @GetMapping
    @RequiresPermission(resource = Resource.DEPARTMENT, action = Action.READ)
    public ResponseEntity<List<DepartmentResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DepartmentStatus status) {
        TenantContext context = TenantContextHolder.require();
        List<DepartmentResponse> departments = listDepartments.list(context.tenantId(), search, status)
            .stream()
            .map(DepartmentResponse::from)
            .toList();
        return ResponseEntity.ok(departments);
    }

    /** Cria departamento no tenant do contexto autenticado. Requer DEPARTMENT:MANAGE. */
    @PostMapping
    @RequiresPermission(resource = Resource.DEPARTMENT, action = Action.MANAGE)
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        TenantContext context = TenantContextHolder.require();
        Department department = createDepartment.create(new CreateDepartmentCommand(
            context.tenantId(),
            request.name(),
            request.description(),
            request.leaderUserId(),
            request.monthlyBudget()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DepartmentResponse.from(department));
    }

    /** Consulta um departamento do tenant. Requer DEPARTMENT:READ. */
    @GetMapping("/{id}")
    @RequiresPermission(resource = Resource.DEPARTMENT, action = Action.READ)
    public ResponseEntity<DepartmentResponse> get(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(DepartmentResponse.from(getDepartment.get(context.tenantId(), id)));
    }

    /** Edita um departamento do tenant. Requer DEPARTMENT:MANAGE. */
    @PutMapping("/{id}")
    @RequiresPermission(resource = Resource.DEPARTMENT, action = Action.MANAGE)
    public ResponseEntity<DepartmentResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody DepartmentRequest request) {
        TenantContext context = TenantContextHolder.require();
        Department department = updateDepartment.update(new UpdateDepartmentCommand(
            context.tenantId(),
            id,
            request.name(),
            request.description(),
            request.leaderUserId(),
            request.monthlyBudget()));
        return ResponseEntity.ok(DepartmentResponse.from(department));
    }

    /** Remove (logicamente) um departamento do tenant. Requer DEPARTMENT:MANAGE. */
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = Resource.DEPARTMENT, action = Action.MANAGE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        TenantContext context = TenantContextHolder.require();
        deleteDepartment.delete(context.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
