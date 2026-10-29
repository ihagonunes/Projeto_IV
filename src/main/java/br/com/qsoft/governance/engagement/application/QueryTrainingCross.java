package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.engagement.domain.TrainingCrossCalculator;
import br.com.qsoft.governance.engagement.domain.TrainingCrossResult;
import br.com.qsoft.governance.engagement.domain.TrainingFact;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

/** Cruzamento treinamento × adoção (ADR-40 §6) com recorte e lista nominal. */
@Service
public class QueryTrainingCross {

    private static final int MAX_NOMINAL = 200;

    private final AuthorizationQuery authorizationQuery;
    private final IdentityQuery identityQuery;
    private final EngagementFactsPort operationalFacts;
    private final EngagementFactsPort historicalFacts;

    public QueryTrainingCross(AuthorizationQuery authorizationQuery,
                              IdentityQuery identityQuery,
                              @Qualifier("operationalEngagementFacts") EngagementFactsPort operationalFacts,
                              @Qualifier("historicalEngagementFacts") EngagementFactsPort historicalFacts) {
        this.authorizationQuery = authorizationQuery;
        this.identityQuery = identityQuery;
        this.operationalFacts = operationalFacts;
        this.historicalFacts = historicalFacts;
    }

    public TrainingCrossReport query(AdoptionCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (command.ate() != null && command.ate().isAfter(today)) {
            throw new IllegalArgumentException("ate não pode ser futuro.");
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
        LocalDate effectiveAte = command.ate() != null
            ? command.ate() : source.latestCoverage(command.tenantId()).orElse(today);
        EngagementFacts facts = source.load(command.tenantId(), effectiveAte);
        selected.retainAll(facts.eligible());

        EngagementFacts scoped = subset(facts, selected);
        TrainingCrossResult cross = TrainingCrossCalculator.of(scoped, effectiveAte);
        Map<UserId, UserContext> selectedContexts = contexts.stream()
            .filter(context -> selected.contains(context.userId()))
            .collect(Collectors.toMap(UserContext::userId, context -> context,
                (left, right) -> left, LinkedHashMap::new));

        boolean[] nominal = new boolean[1];
        TrainingCrossReport.QuadrantReport trainedActive = report(
            cross.trainedActive(), selectedContexts, command.tenantId(), command.actor(), nominal);
        TrainingCrossReport.QuadrantReport trainedInactive = report(
            cross.trainedInactive(), selectedContexts, command.tenantId(), command.actor(), nominal);
        TrainingCrossReport.QuadrantReport notTrainedActive = report(
            cross.notTrainedActive(), selectedContexts, command.tenantId(), command.actor(), nominal);
        TrainingCrossReport.QuadrantReport notTrainedInactive = report(
            cross.notTrainedInactive(), selectedContexts, command.tenantId(), command.actor(),
            nominal);

        return new TrainingCrossReport(command.source(), effectiveAte,
            trainedActive, trainedInactive, notTrainedActive, notTrainedInactive,
            nominal[0], facts.coverage());
    }

    private TrainingCrossReport.QuadrantReport report(TrainingCrossResult.Quadrant quadrant,
                                                      Map<UserId, UserContext> contexts,
                                                      TenantId tenantId, UserId actor,
                                                      boolean[] nominal) {
        List<TrainingCrossReport.UserView> visible = new ArrayList<>();
        for (UserId userId : quadrant.users().stream()
            .sorted(Comparator.comparing(user -> user.value().toString())).toList()) {
            UserContext context = contexts.get(userId);
            UUID departmentId = context == null ? null : context.departmentId();
            if (authorizationQuery.isAllowed(tenantId, actor, Resource.USER,
                    Action.READ, new AccessTarget.OwnedTarget(userId, departmentId))) {
                visible.add(new TrainingCrossReport.UserView(userId,
                    context == null ? null : context.name(), departmentId));
            }
        }
        boolean truncated = visible.size() > MAX_NOMINAL;
        List<TrainingCrossReport.UserView> limited = truncated
            ? visible.subList(0, MAX_NOMINAL) : visible;
        if (!limited.isEmpty()) {
            nominal[0] = true;
        }
        return new TrainingCrossReport.QuadrantReport(quadrant.total(), limited, truncated);
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
