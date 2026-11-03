package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.application.port.CreditsSnapshotRepository;
import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.CreditsCalculator;
import br.com.qsoft.governance.engagement.domain.CreditsItem;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.engagement.domain.TrainingFact;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** AI Credits com snapshot imutável de mês fechado (ADR-19/ADR-27/ADR-40 §7). */
@Service
public class QueryCredits {

    private final AuthorizationQuery authorizationQuery;
    private final IdentityQuery identityQuery;
    private final EngagementFactsPort operationalFacts;
    private final EngagementFactsPort historicalFacts;
    private final CreditsSnapshotRepository snapshotRepository;

    public QueryCredits(AuthorizationQuery authorizationQuery,
                        IdentityQuery identityQuery,
                        @Qualifier("operationalEngagementFacts") EngagementFactsPort operationalFacts,
                        @Qualifier("historicalEngagementFacts") EngagementFactsPort historicalFacts,
                        CreditsSnapshotRepository snapshotRepository) {
        this.authorizationQuery = authorizationQuery;
        this.identityQuery = identityQuery;
        this.operationalFacts = operationalFacts;
        this.historicalFacts = historicalFacts;
        this.snapshotRepository = snapshotRepository;
    }

    public CreditsReport query(CreditsCommand command) {
        Objects.requireNonNull(command, "command");
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        YearMonth month = command.month() == null ? current : command.month();
        if (month.isAfter(current)) {
            throw new IllegalArgumentException("mes não pode ser futuro.");
        }

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            command.tenantId(), command.actor(), Resource.USAGE, Action.READ);
        if (visibility instanceof VisibilityScope.None) {
            throw new EngagementAccessDeniedException();
        }

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
            .filter(context -> isVisible(visibility, context))
            .filter(context -> command.departmentId() == null
                || command.departmentId().equals(context.departmentId()))
            .map(UserContext::userId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        EngagementFactsPort source = command.source() == SourceKind.HISTORICO
            ? historicalFacts : operationalFacts;
        LocalDate endOfMonth = month.atEndOfMonth();
        EngagementFacts facts = source.load(command.tenantId(), endOfMonth);
        selected.retainAll(facts.eligible());

        boolean closed = month.isBefore(current);
        Map<UserId, CreditsItem> items;
        if (closed) {
            items = snapshotRepository.find(command.tenantId(), command.source(), month,
                CreditsCalculator.CALCULATION_VERSION);
            if (items.isEmpty()) {
                List<CreditsItem> calculated = CreditsCalculator.calculate(facts, month);
                snapshotRepository.insertIfAbsent(command.tenantId(), command.source(), month,
                    CreditsCalculator.CALCULATION_VERSION, calculated);
                items = snapshotRepository.find(command.tenantId(), command.source(), month,
                    CreditsCalculator.CALCULATION_VERSION);
                if (items.isEmpty()) {
                    items = calculated.stream().collect(Collectors.toMap(
                        CreditsItem::userId, item -> item, (left, right) -> left,
                        LinkedHashMap::new));
                }
            }
        } else {
            items = CreditsCalculator.calculate(subset(facts, selected), month).stream()
                .collect(Collectors.toMap(CreditsItem::userId, item -> item,
                    (left, right) -> left, LinkedHashMap::new));
        }

        Map<UserId, UserContext> selectedContexts = contexts.stream()
            .filter(context -> selected.contains(context.userId()))
            .collect(Collectors.toMap(UserContext::userId, context -> context,
                (left, right) -> left, LinkedHashMap::new));
        Map<UserId, CreditsItem> resolved = items;
        List<CreditsReport.Item> views = selected.stream()
            .filter(resolved::containsKey)
            .map(userId -> view(userId, resolved.get(userId), selectedContexts.get(userId)))
            .sorted(Comparator.comparing(CreditsReport.Item::score,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(item -> item.userId().value().toString()))
            .toList();

        return new CreditsReport(command.source(), month,
            closed ? "FECHADO" : "PROVISORIO", CreditsCalculator.CALCULATION_VERSION,
            views, facts.coverage());
    }

    private static CreditsReport.Item view(UserId userId, CreditsItem item,
                                           UserContext context) {
        return new CreditsReport.Item(userId,
            context == null ? null : context.name(),
            context == null ? null : context.departmentId(),
            item.score(), item.usage(), item.training(), item.license(),
            item.licenseApplicability().name(), item.dataStatus().name());
    }

    private static boolean isVisible(VisibilityScope visibility, UserContext context) {
        if (visibility instanceof VisibilityScope.TenantWide) {
            return true;
        }
        if (visibility instanceof VisibilityScope.Departments departments) {
            return departments.self().equals(context.userId())
                || (context.departmentId() != null
                    && departments.ids().contains(context.departmentId()));
        }
        if (visibility instanceof VisibilityScope.SelfOnly selfOnly) {
            return selfOnly.self().equals(context.userId());
        }
        return false;
    }

    private static EngagementFacts subset(EngagementFacts facts, Set<UserId> ids) {
        Map<UserId, Set<LocalDate>> activeDays = facts.activeDays().entrySet().stream()
            .filter(entry -> ids.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (left, right) -> left, LinkedHashMap::new));
        Map<UserId, List<LicenseFact>> licenses = facts.licenses().entrySet().stream()
            .filter(entry -> ids.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (left, right) -> left, LinkedHashMap::new));
        Map<UserId, List<TrainingFact>> trainings = facts.trainings().entrySet().stream()
            .filter(entry -> ids.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (left, right) -> left, LinkedHashMap::new));
        return new EngagementFacts(ids, activeDays, licenses, trainings,
            facts.coverage(), facts.licenseSourceAvailable(), facts.trainingSourceAvailable());
    }
}
