package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.AdoptionCalculator;
import br.com.qsoft.governance.engagement.domain.AdoptionResult;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Consulta de adoção com recorte autorizado e duas fontes isoladas (ADR-26/ADR-40). */
@Service
public class QueryAdoption {

    private final AuthorizationQuery authorizationQuery;
    private final IdentityQuery identityQuery;
    private final EngagementFactsPort operationalFacts;
    private final EngagementFactsPort historicalFacts;

    public QueryAdoption(AuthorizationQuery authorizationQuery,
                         IdentityQuery identityQuery,
                         @Qualifier("operationalEngagementFacts") EngagementFactsPort operationalFacts,
                         @Qualifier("historicalEngagementFacts") EngagementFactsPort historicalFacts) {
        this.authorizationQuery = authorizationQuery;
        this.identityQuery = identityQuery;
        this.operationalFacts = operationalFacts;
        this.historicalFacts = historicalFacts;
    }

    public AdoptionReport query(AdoptionCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (command.ate() != null && command.ate().isAfter(today)) {
            throw new IllegalArgumentException("ate não pode ser futuro.");
        }

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            command.tenantId(), command.actor(), Resource.USAGE, Action.READ);
        Predicate<UserContext> visible = EngagementVisibility.forScope(visibility);

        List<UserContext> contexts = identityQuery.eligibleUsers(command.tenantId());
        Set<UUID> eligibleDepartments = contexts.stream()
            .map(UserContext::departmentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (command.departmentId() != null
            && (!visibility.coversDepartment(command.departmentId())
                || !eligibleDepartments.contains(command.departmentId()))) {
            throw new EngagementAccessDeniedException();
        }

        Set<UserId> selected = contexts.stream()
            .filter(visible)
            .filter(context -> command.departmentId() == null
                || command.departmentId().equals(context.departmentId()))
            .map(UserContext::userId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        EngagementFactsPort source = command.source() == SourceKind.HISTORICO
            ? historicalFacts : operationalFacts;
        LocalDate effectiveAte = command.ate() != null
            ? command.ate() : source.latestCoverage(command.tenantId()).orElse(today);
        EngagementFacts facts = source.load(command.tenantId(), effectiveAte);
        selected.retainAll(facts.eligible());

        EngagementFacts scoped = facts.restrictTo(selected);
        AdoptionResult overall = AdoptionCalculator.of(scoped, effectiveAte);
        List<AdoptionReport.DepartmentAdoption> byDepartment =
            byDepartment(contexts, selected, facts, effectiveAte);

        return new AdoptionReport(command.source(), effectiveAte,
            AdoptionCalculator.WINDOW_DAYS, overall, byDepartment, facts.coverage());
    }

    private static List<AdoptionReport.DepartmentAdoption> byDepartment(
            List<UserContext> contexts, Set<UserId> selected, EngagementFacts facts,
            LocalDate ate) {
        Map<UserId, UserContext> selectedContexts = contexts.stream()
            .filter(context -> selected.contains(context.userId()))
            .collect(Collectors.toMap(UserContext::userId, context -> context,
                (left, right) -> left, LinkedHashMap::new));
        Map<UUID, Set<UserId>> groups = new LinkedHashMap<>();
        for (UserContext context : selectedContexts.values()) {
            groups.computeIfAbsent(context.departmentId(),
                ignored -> new LinkedHashSet<>()).add(context.userId());
        }
        return groups.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(
                Comparator.comparing(UUID::toString))))
            .map(entry -> new AdoptionReport.DepartmentAdoption(entry.getKey(),
                AdoptionCalculator.of(facts.restrictTo(entry.getValue()), ate)))
            .toList();
    }

}
