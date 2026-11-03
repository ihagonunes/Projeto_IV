package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.AdoptionCommand;
import br.com.qsoft.governance.engagement.application.QueryAdoption;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API de adoção do engajamento (SPI-143/SPEC-06 §5.1). */
@RestController
@RequestMapping("/api/v1/engajamento")
public class EngagementAdoptionController {

    private final QueryAdoption queryAdoption;

    public EngagementAdoptionController(QueryAdoption queryAdoption) {
        this.queryAdoption = queryAdoption;
    }

    @GetMapping("/adocao")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<AdoptionResponse> adoption(
            @RequestParam(required = false) String fonte,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) UUID departmentId) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(AdoptionResponse.from(queryAdoption.query(
            new AdoptionCommand(context.tenantId(), context.userId(),
                parseSource(fonte), ate, departmentId))));
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
}
