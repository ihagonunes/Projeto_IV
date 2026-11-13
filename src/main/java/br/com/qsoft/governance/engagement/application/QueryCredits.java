package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.application.port.CreditsSnapshotRepository;
import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.CreditsCalculator;
import br.com.qsoft.governance.engagement.domain.CreditsItem;
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
import java.util.function.Predicate;
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
        LocalDate endOfMonth = month.atEndOfMonth();
        EngagementFacts facts = source.load(command.tenantId(), endOfMonth);
        selected.retainAll(facts.eligible());

        UUID ownDepartment = contexts.stream()
            .filter(context -> context.userId().equals(command.actor()))
            .filter(context -> "COLLABORATOR".equals(context.hierarchyLevel()))
            .findFirst().map(UserContext::departmentId).orElse(null);
        Set<UserId> peers = new LinkedHashSet<>();
        if (selected.contains(command.actor()) && ownDepartment != null) {
            contexts.stream().filter(context -> ownDepartment.equals(context.departmentId()))
                .map(UserContext::userId).filter(facts.eligible()::contains).forEach(peers::add);
        }

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
                        CreditsItem::userId, item -> item));
                }
            }
        } else {
            Set<UserId> calculationScope = new LinkedHashSet<>(selected);
            calculationScope.addAll(peers);
            items = CreditsCalculator.calculate(facts.restrictTo(calculationScope), month).stream()
                .collect(Collectors.toMap(CreditsItem::userId, item -> item));
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
            views, facts.coverage(), rank(command.actor(), peers, resolved));
    }

    private static Integer rank(UserId actor, Set<UserId> peers, Map<UserId, CreditsItem> items) {
        CreditsItem own = items.get(actor);
        if (!peers.contains(actor) || own == null || own.score() == null) return null;
        return 1 + (int) peers.stream().map(items::get).filter(Objects::nonNull)
            .filter(peer -> peer.score() != null && peer.dataStatus() == own.dataStatus())
            .filter(peer -> peer.score().compareTo(own.score()) > 0).count();
    }

    private static CreditsReport.Item view(UserId userId, CreditsItem item,
                                           UserContext context) {
        return new CreditsReport.Item(userId,
            context.name(),
            context.departmentId(),
            item.score(), item.usage(), item.training(), item.license(),
            item.licenseApplicability().name(), item.dataStatus().name());
    }

}
