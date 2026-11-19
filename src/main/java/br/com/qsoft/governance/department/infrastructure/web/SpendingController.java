package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.application.ManageSpending;
import br.com.qsoft.governance.department.application.port.SpendingStore;
import br.com.qsoft.governance.department.domain.SpendingPlan;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** As decisões de acesso ficam nos casos de uso; nenhum tenant é recebido do cliente. */
@RestController
@RequestMapping("/api/v1/orcamento-ia")
public class SpendingController {
    private final ManageSpending service;
    public SpendingController(ManageSpending service) { this.service = service; }

    @GetMapping("/me")
    public ManageSpending.Balance own() {
        var context = TenantContextHolder.require();
        return service.own(context.tenantId(), context.userId());
    }
    @GetMapping
    public ManageSpending.Overview overview(@RequestParam(required = false) String month) {
        var context = TenantContextHolder.require();
        return service.overview(context.tenantId(), context.userId(), month == null ? null : YearMonth.parse(month));
    }
    @PutMapping("/{month}")
    public void save(@PathVariable String month, @RequestBody SpendingPlan plan) {
        var context = TenantContextHolder.require();
        service.save(context.tenantId(), context.userId(), YearMonth.parse(month), plan);
    }
    @PostMapping("/sugestao")
    public SpendingPlan suggest(@RequestBody SpendingPlan plan) {
        var context = TenantContextHolder.require();
        return service.suggest(context.tenantId(), context.userId(), plan);
    }
    public record AlertRequest(int percentage) {}
    @GetMapping("/alerta")
    public SpendingStore.Settings alert() {
        var context = TenantContextHolder.require();
        return service.settings(context.tenantId(), context.userId());
    }
    @PutMapping("/alerta")
    public void alert(@RequestBody AlertRequest request) {
        var context = TenantContextHolder.require();
        service.alert(context.tenantId(), context.userId(), request.percentage());
    }
    public record TimezoneRequest(String timezone) {}
    @PutMapping("/fuso")
    public void timezone(@RequestBody TimezoneRequest request) {
        var context = TenantContextHolder.require();
        service.timezone(context.tenantId(), context.userId(), request.timezone());
    }
    public record Reconciliation(BigDecimal cost, String reference) {}
    @PutMapping("/reservas/{requestId}")
    public void reconcile(@PathVariable UUID requestId, @RequestBody Reconciliation request) {
        var context = TenantContextHolder.require();
        service.reconcile(context.tenantId(), context.userId(), requestId, request.cost(), request.reference());
    }
}
