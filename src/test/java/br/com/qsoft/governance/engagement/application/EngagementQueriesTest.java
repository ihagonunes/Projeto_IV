package br.com.qsoft.governance.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.engagement.application.port.CreditsSnapshotRepository;
import br.com.qsoft.governance.engagement.application.port.EngagementFactsPort;
import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.CreditsItem;
import br.com.qsoft.governance.engagement.domain.DataStatus;
import br.com.qsoft.governance.engagement.domain.EngagementFacts;
import br.com.qsoft.governance.engagement.domain.LicenseApplicability;
import br.com.qsoft.governance.engagement.domain.LicenseFact;
import br.com.qsoft.governance.engagement.domain.SourceKind;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class EngagementQueriesTest {

    private static final YearMonth CLOSED_MONTH = YearMonth.of(2026, 7);
    private static final LocalDate REFERENCE = CLOSED_MONTH.atEndOfMonth();
    private static final String VERSION = "credits-v1";
    private static final SourceKind OPERATIONAL = SourceKind.OPERACIONAL;
    private static final SourceKind HISTORICAL = SourceKind.HISTORICO;

    private final TenantId tenant = TenantId.random();
    private final UserId actor = UserId.random();
    private final UserId teammate = UserId.random();
    private final UserId otherDepartmentUser = UserId.random();
    private final UserId unassigned = UserId.random();
    private final UserId absentFromFacts = UserId.random();
    private final UUID department = new UUID(0, 1);
    private final UUID otherDepartment = new UUID(0, 2);
    private final AuthorizationQuery authorization = mock(AuthorizationQuery.class);
    private final IdentityQuery identity = mock(IdentityQuery.class);
    private final EngagementFactsPort operational = mock(EngagementFactsPort.class);
    private final EngagementFactsPort historical = mock(EngagementFactsPort.class);
    private final CreditsSnapshotRepository snapshots = mock(CreditsSnapshotRepository.class);
    private final QueryAdoption adoption = new QueryAdoption(authorization, identity, operational, historical);
    private final QueryTrainingCross training = new QueryTrainingCross(authorization, identity, operational, historical);
    private final QueryCredits credits = new QueryCredits(authorization, identity, operational, historical, snapshots);

    @Test
    void rejectsFuturePeriodsBeforeConsultingDependencies() {
        LocalDate future = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        YearMonth futureMonth = YearMonth.now(ZoneOffset.UTC).plusMonths(1);
        AdoptionCommand command = adoptionCommand(OPERATIONAL, future, null);

        assertThatThrownBy(() -> adoption.query(command)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> training.query(command)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credits.query(creditsCommand(OPERATIONAL, futureMonth, null)))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(authorization, identity, operational, historical, snapshots);
    }

    @Test
    void deniesAllQueriesWithoutUsageReadVisibility() {
        allow(new VisibilityScope.None());

        assertDenied(null);

        verifyNoInteractions(identity, operational, historical, snapshots);
    }

    @Test
    void deniesDepartmentOutsideVisibilityAndDepartmentWithoutEligibleUsers() {
        users();
        allow(new VisibilityScope.Departments(Set.of(department), actor));
        assertDenied(otherDepartment);

        allow(new VisibilityScope.TenantWide());
        assertDenied(UUID.randomUUID());

        verifyNoInteractions(operational, historical, snapshots);
    }

    @Test
    void usesHistoricalCoverageDateAndGroupsAdoptionByDepartmentWithUnassignedLast() {
        users();
        allow(new VisibilityScope.TenantWide());
        when(historical.latestCoverage(tenant)).thenReturn(Optional.of(REFERENCE));
        when(historical.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));

        AdoptionReport report = adoption.query(adoptionCommand(HISTORICAL, null, null));

        assertThat(report.sourceKind()).isEqualTo(HISTORICAL);
        assertThat(report.ate()).isEqualTo(REFERENCE);
        assertThat(report.windowDays()).isEqualTo(30);
        assertThat(report.overall().eligible()).isEqualTo(4);
        assertThat(report.overall().active()).isEqualTo(2);
        assertThat(report.overall().idleLicenses()).isEqualTo(1);
        assertThat(report.byDepartment()).extracting(AdoptionReport.DepartmentAdoption::departmentId)
            .containsExactly(department, otherDepartment, null);
        assertThat(report.byDepartment()).extracting(group -> group.result().eligible()).containsExactly(2, 1, 1);
        assertThat(report.coverage()).isEqualTo(new Coverage(5, 4, 1, 2));
        verifyNoInteractions(operational);
    }

    @Test
    void duplicateIdentityRowsCountOnceAndKeepTheFirstContext() {
        allow(new VisibilityScope.TenantWide());
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(actor, department, "Primeiro"),
            context(actor, otherDepartment, "Duplicado")));
        when(operational.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        when(authorization.isAllowed(tenant, actor, Resource.USER, Action.READ,
            new AccessTarget.OwnedTarget(actor, department))).thenReturn(true);
        when(snapshots.find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION))
            .thenReturn(Map.of(actor, item(actor, "40", DataStatus.COMPLETE)));
        AdoptionCommand command = adoptionCommand(OPERATIONAL, REFERENCE, null);

        AdoptionReport adoptionReport = adoption.query(command);
        TrainingCrossReport trainingReport = training.query(command);
        CreditsReport creditsReport = credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, null));

        assertThat(adoptionReport.overall().eligible()).isEqualTo(1);
        assertThat(adoptionReport.byDepartment()).extracting(AdoptionReport.DepartmentAdoption::departmentId)
            .containsExactly(department);
        assertThat(trainingReport.trainedActive().total()).isEqualTo(1);
        assertThat(trainingReport.trainedActive().users())
            .containsExactly(new TrainingCrossReport.UserView(actor, "Primeiro", department));
        assertThat(creditsReport.items()).singleElement().satisfies(view -> {
            assertThat(view.userId()).isEqualTo(actor);
            assertThat(view.name()).isEqualTo("Primeiro");
            assertThat(view.departmentId()).isEqualTo(department);
        });
    }

    @Test
    void defaultsToUtcTodayWithoutCoverageDateAndKeepsOnlyTheActorsOwnFacts() {
        users();
        allow(new VisibilityScope.SelfOnly(actor));
        when(operational.latestCoverage(tenant)).thenReturn(Optional.empty());
        when(operational.load(eq(tenant), any(LocalDate.class)))
            .thenAnswer(invocation -> facts(invocation.getArgument(1)));
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        AdoptionReport adoptionReport = adoption.query(adoptionCommand(OPERATIONAL, null, null));
        TrainingCrossReport trainingReport = training.query(adoptionCommand(OPERATIONAL, null, null));
        LocalDate after = LocalDate.now(ZoneOffset.UTC);

        assertThat(adoptionReport.ate()).isIn(before, after);
        assertThat(adoptionReport.overall().eligible()).isEqualTo(1);
        assertThat(adoptionReport.overall().active()).isEqualTo(1);
        assertThat(trainingReport.ate()).isIn(before, after);
        assertThat(trainingReport.trainedActive().total()).isEqualTo(1);
        assertThat(trainingReport.trainedActive().users()).isEmpty();
        assertThat(trainingReport.nominal()).isFalse();
        verifyNoInteractions(historical);
    }

    @Test
    void departmentVisibilityIncludesSelfAndExplicitFilterRemovesSelfOutsideTheDepartment() {
        users();
        allow(new VisibilityScope.Departments(Set.of(otherDepartment), actor));
        when(operational.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        when(snapshots.find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION)).thenReturn(Map.of(
            actor, item(actor, "40", DataStatus.COMPLETE),
            teammate, item(teammate, "80", DataStatus.COMPLETE),
            otherDepartmentUser, item(otherDepartmentUser, "60", DataStatus.COMPLETE),
            unassigned, item(unassigned, "90", DataStatus.COMPLETE)));

        AdoptionReport unfiltered = adoption.query(adoptionCommand(OPERATIONAL, REFERENCE, null));
        AdoptionCommand filtered = adoptionCommand(OPERATIONAL, REFERENCE, otherDepartment);
        AdoptionReport filteredAdoption = adoption.query(filtered);
        TrainingCrossReport filteredTraining = training.query(filtered);
        CreditsReport filteredCredits = credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, otherDepartment));

        assertThat(unfiltered.byDepartment()).extracting(AdoptionReport.DepartmentAdoption::departmentId)
            .containsExactly(department, otherDepartment);
        assertThat(filteredAdoption.overall().eligible()).isEqualTo(1);
        assertThat(filteredAdoption.byDepartment()).extracting(AdoptionReport.DepartmentAdoption::departmentId)
            .containsExactly(otherDepartment);
        assertThat(filteredTraining.trainedActive().total()).isZero();
        assertThat(filteredTraining.notTrainedActive().total()).isEqualTo(1);
        assertThat(filteredCredits.items()).extracting(CreditsReport.Item::userId).containsExactly(otherDepartmentUser);
        assertThat(filteredCredits.personalRank()).isNull();
    }

    @Test
    void trainingTotalsIncludeUsersWithoutNominalPermissionButNamesRequireUserRead() {
        users();
        allow(new VisibilityScope.Departments(Set.of(department, otherDepartment), actor));
        when(historical.latestCoverage(tenant)).thenReturn(Optional.of(REFERENCE));
        when(historical.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        when(authorization.isAllowed(tenant, actor, Resource.USER, Action.READ,
            new AccessTarget.OwnedTarget(teammate, department))).thenReturn(true);

        TrainingCrossReport report = training.query(adoptionCommand(HISTORICAL, null, null));

        assertThat(report.ate()).isEqualTo(REFERENCE);
        assertThat(report.nominal()).isTrue();
        assertThat(report.trainedActive().total()).isEqualTo(1);
        assertThat(report.trainedActive().users()).isEmpty();
        assertThat(report.trainedInactive().users())
            .containsExactly(new TrainingCrossReport.UserView(teammate, "Colega", department));
        assertThat(report.notTrainedActive().total()).isEqualTo(1);
        assertThat(report.notTrainedInactive().total()).isZero();
        verify(authorization).isAllowed(tenant, actor, Resource.USER, Action.READ,
            new AccessTarget.OwnedTarget(actor, department));
        verifyNoInteractions(operational);
    }

    @Test
    void trainingCapsNominalQuadrantAtTwoHundredAndPreservesTotalAndStableOrder() {
        allow(new VisibilityScope.TenantWide());
        List<UserContext> contexts = IntStream.rangeClosed(1, 201)
            .mapToObj(index -> context(UserId.of(new UUID(0, index)), department, "Pessoa " + index))
            .toList().reversed();
        Set<UserId> ids = contexts.stream().map(UserContext::userId).collect(Collectors.toSet());
        when(identity.eligibleUsers(tenant)).thenReturn(contexts);
        when(operational.load(tenant, REFERENCE)).thenReturn(new EngagementFacts(
            ids, Map.of(), Map.of(), Map.of(), new Coverage(201, 201, 0, 0)));
        when(authorization.isAllowed(eq(tenant), eq(actor), eq(Resource.USER), eq(Action.READ),
            any(AccessTarget.OwnedTarget.class))).thenReturn(true);

        TrainingCrossReport.QuadrantReport quadrant =
            training.query(adoptionCommand(OPERATIONAL, REFERENCE, null)).notTrainedInactive();

        assertThat(quadrant.total()).isEqualTo(201);
        assertThat(quadrant.truncated()).isTrue();
        assertThat(quadrant.users()).extracting(TrainingCrossReport.UserView::userId)
            .containsExactlyElementsOf(contexts.reversed().subList(0, 200).stream().map(UserContext::userId).toList());
    }

    @Test
    void creditsUseClosedHistoricalSnapshotAndSortByScoreThenIdWithNullLast() {
        users();
        allow(new VisibilityScope.TenantWide());
        when(historical.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        when(snapshots.find(tenant, HISTORICAL, CLOSED_MONTH, VERSION)).thenReturn(Map.of(
            actor, item(actor, "40", DataStatus.COMPLETE),
            teammate, item(teammate, "40", DataStatus.COMPLETE),
            otherDepartmentUser, item(otherDepartmentUser, "80", DataStatus.COMPLETE),
            unassigned, item(unassigned, null, DataStatus.PARTIAL),
            absentFromFacts, item(absentFromFacts, "99", DataStatus.COMPLETE)));

        CreditsReport report = credits.query(creditsCommand(HISTORICAL, CLOSED_MONTH, null));

        List<UserId> tied = List.of(actor, teammate).stream().sorted(Comparator.comparing(UserId::toString)).toList();
        assertThat(report.items()).extracting(CreditsReport.Item::userId)
            .containsExactly(otherDepartmentUser, tied.getFirst(), tied.getLast(), unassigned);
        assertThat(report.items().getFirst().name()).isEqualTo("Outro departamento");
        assertThat(report.items().getLast().departmentId()).isNull();
        assertThat(report.personalRank()).isEqualTo(1);
        assertThat(report.status()).isEqualTo("FECHADO");
        assertThat(report.calculationVersion()).isEqualTo(VERSION);
        assertThat(report.coverage()).isEqualTo(new Coverage(5, 4, 1, 2));
        verify(snapshots).find(tenant, HISTORICAL, CLOSED_MONTH, VERSION);
        verifyNoMoreInteractions(snapshots);
        verifyNoInteractions(operational);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void creditsInsertMissingClosedSnapshotAndPreferStoredValuesOnReadBack(boolean snapshotVisible) {
        users();
        allow(new VisibilityScope.SelfOnly(actor));
        when(operational.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        Map<UserId, CreditsItem> reread = snapshotVisible
            ? Map.of(actor, item(actor, "73", DataStatus.COMPLETE)) : Map.of();
        when(snapshots.find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION)).thenReturn(Map.of(), reread);

        CreditsReport report = credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, null));

        assertThat(report.items()).singleElement().satisfies(view -> {
            assertThat(view.userId()).isEqualTo(actor);
            assertThat(view.score()).isEqualByComparingTo(snapshotVisible ? "73" : "43.75");
        });
        var order = inOrder(snapshots);
        order.verify(snapshots).find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION);
        ArgumentCaptor<List<CreditsItem>> inserted = ArgumentCaptor.captor();
        order.verify(snapshots).insertIfAbsent(eq(tenant), eq(OPERATIONAL), eq(CLOSED_MONTH), eq(VERSION), inserted.capture());
        assertThat(inserted.getValue()).extracting(CreditsItem::userId)
            .containsExactlyInAnyOrder(actor, teammate, otherDepartmentUser, unassigned);
        order.verify(snapshots).find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION);
    }

    @Test
    void creditsDefaultToCurrentMonthAndRankPeersWithoutExposingTheirRowsOrWritingSnapshot() {
        allow(new VisibilityScope.SelfOnly(actor));
        users();
        when(operational.load(eq(tenant), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate endOfMonth = invocation.getArgument(1);
            return new EngagementFacts(Set.of(actor, teammate, otherDepartmentUser),
                Map.of(teammate, Set.of(endOfMonth), otherDepartmentUser, Set.of(endOfMonth)),
                Map.of(actor, List.of(new LicenseFact(null, endOfMonth, null, true)),
                    otherDepartmentUser, List.of(new LicenseFact(null, endOfMonth, null, true))),
                Map.of(teammate, List.of(new TrainingFact("IA", endOfMonth)),
                    otherDepartmentUser, List.of(new TrainingFact("IA", endOfMonth))), new Coverage(5, 3, 0, 0));
        });
        YearMonth before = YearMonth.now(ZoneOffset.UTC);

        CreditsReport report = credits.query(creditsCommand(OPERATIONAL, null, null));

        assertThat(report.month()).isIn(before, YearMonth.now(ZoneOffset.UTC));
        assertThat(report.status()).isEqualTo("PROVISORIO");
        assertThat(report.items()).extracting(CreditsReport.Item::userId).containsExactly(actor);
        assertThat(report.personalRank()).isEqualTo(2);
        verify(operational).load(tenant, report.month().atEndOfMonth());
        verifyNoInteractions(snapshots, historical);
    }

    @Test
    void creditsRankIgnoresPeersWithMissingOrIncompatibleScores() {
        UserId partialPeer = UserId.random();
        UserId unscoredPeer = UserId.random();
        allow(new VisibilityScope.SelfOnly(actor));
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(actor, department, "Ator"),
            context(teammate, department, "Sem snapshot"), context(partialPeer, department, "Parcial"),
            context(unscoredPeer, department, "Sem score")));
        when(operational.load(tenant, REFERENCE)).thenReturn(new EngagementFacts(
            Set.of(actor, teammate, partialPeer, unscoredPeer), Map.of(), Map.of(), Map.of(), new Coverage(4, 4, 0, 0)));
        when(snapshots.find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION)).thenReturn(Map.of(
            actor, item(actor, "40", DataStatus.COMPLETE), partialPeer, item(partialPeer, "80", DataStatus.PARTIAL),
            unscoredPeer, item(unscoredPeer, null, DataStatus.COMPLETE)));

        CreditsReport report = credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, null));

        assertThat(report.personalRank()).isEqualTo(1);
        assertThat(report.items()).extracting(CreditsReport.Item::userId).containsExactly(actor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void creditsOmitPersonalRankWhenActorHasNoScore(boolean hasSnapshotRow) {
        users();
        allow(new VisibilityScope.SelfOnly(actor));
        when(operational.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        CreditsItem peer = item(teammate, "80", DataStatus.COMPLETE);
        Map<UserId, CreditsItem> stored = hasSnapshotRow
            ? Map.of(teammate, peer, actor, item(actor, null, DataStatus.INSUFFICIENT_DATA))
            : Map.of(teammate, peer);
        when(snapshots.find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION)).thenReturn(stored);

        CreditsReport report = credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, null));

        assertThat(report.personalRank()).isNull();
        assertThat(report.items()).extracting(CreditsReport.Item::userId)
            .containsExactlyElementsOf(hasSnapshotRow ? List.of(actor) : List.of());
    }

    @Test
    void creditsOmitPersonalRankForActorWithoutDepartment() {
        allow(new VisibilityScope.SelfOnly(actor));
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(actor, null, "Sem lotação")));
        when(operational.load(tenant, REFERENCE)).thenReturn(facts(REFERENCE));
        when(snapshots.find(tenant, OPERATIONAL, CLOSED_MONTH, VERSION))
            .thenReturn(Map.of(actor, item(actor, "40", DataStatus.COMPLETE)));

        CreditsReport report = credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, null));

        assertThat(report.personalRank()).isNull();
        assertThat(report.items()).extracting(CreditsReport.Item::userId).containsExactly(actor);
    }

    private void assertDenied(UUID requestedDepartment) {
        AdoptionCommand command = adoptionCommand(OPERATIONAL, REFERENCE, requestedDepartment);
        assertThatThrownBy(() -> adoption.query(command)).isInstanceOf(EngagementAccessDeniedException.class);
        assertThatThrownBy(() -> training.query(command)).isInstanceOf(EngagementAccessDeniedException.class);
        assertThatThrownBy(() -> credits.query(creditsCommand(OPERATIONAL, CLOSED_MONTH, requestedDepartment)))
            .isInstanceOf(EngagementAccessDeniedException.class);
    }

    private void allow(VisibilityScope scope) {
        when(authorization.visibilityFor(tenant, actor, Resource.USAGE, Action.READ)).thenReturn(scope);
    }

    private void users() {
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(context(actor, department, "Ator"),
            context(teammate, department, "Colega"), context(otherDepartmentUser, otherDepartment, "Outro departamento"),
            context(unassigned, null, "Sem lotação"), context(absentFromFacts, department, "Sem fatos")));
    }

    private UserContext context(UserId user, UUID lot, String name) {
        return new UserContext(user, tenant, "COLLABORATOR", lot, "ACTIVE", name);
    }

    private EngagementFacts facts(LocalDate date) {
        return new EngagementFacts(Set.of(actor, teammate, otherDepartmentUser, unassigned),
            Map.of(actor, Set.of(date), otherDepartmentUser, Set.of(date)),
            Map.of(teammate, List.of(new LicenseFact(null, date, null, true))),
            Map.of(actor, List.of(new TrainingFact("IA", date)), teammate, List.of(new TrainingFact("IA", date))),
            new Coverage(5, 4, 1, 2));
    }

    private AdoptionCommand adoptionCommand(SourceKind source, LocalDate date, UUID requestedDepartment) {
        return new AdoptionCommand(tenant, actor, source, date, requestedDepartment);
    }

    private CreditsCommand creditsCommand(SourceKind source, YearMonth month, UUID requestedDepartment) {
        return new CreditsCommand(tenant, actor, source, month, requestedDepartment);
    }

    private static CreditsItem item(UserId user, String score, DataStatus status) {
        return new CreditsItem(user, BigDecimal.ZERO, BigDecimal.ZERO, null,
            LicenseApplicability.NOT_APPLICABLE, score == null ? null : new BigDecimal(score), status);
    }
}
