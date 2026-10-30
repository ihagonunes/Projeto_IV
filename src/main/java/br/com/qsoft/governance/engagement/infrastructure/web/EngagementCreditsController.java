package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.CreditsCommand;
import br.com.qsoft.governance.engagement.application.QueryCredits;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI Credits (SPI-147/SPEC-06 §5.3). */
@RestController
@RequestMapping("/api/v1/engajamento")
public class EngagementCreditsController {

    private final QueryCredits queryCredits;

    public EngagementCreditsController(QueryCredits queryCredits) {
        this.queryCredits = queryCredits;
    }

    @GetMapping("/creditos")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<CreditsResponse> credits(
            @RequestParam(required = false) String fonte,
            @RequestParam(required = false) String mes,
            @RequestParam(required = false) UUID departmentId) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(CreditsResponse.from(queryCredits.query(
            new CreditsCommand(context.tenantId(), context.userId(), parseSource(fonte),
                parseMonth(mes), departmentId))));
    }

    private static SourceKind parseSource(String fonte) {
        if (fonte == null || fonte.isBlank()) {
            return SourceKind.OPERACIONAL;
        }
        try {
            return SourceKind.valueOf(fonte.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("fonte inválida.");
        }
    }

    private static YearMonth parseMonth(String mes) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(ZoneOffset.UTC);
        }
        try {
            return YearMonth.parse(mes.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("mes inválido.");
        }
    }
}
