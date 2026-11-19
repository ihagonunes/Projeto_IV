package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.api.spending.SpendingBudget;
import br.com.qsoft.governance.department.api.spending.SpendingDeniedException;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.SpendingStore;
import br.com.qsoft.governance.department.domain.SpendingPlan;
import br.com.qsoft.governance.department.domain.SpendingPlan.Allocation;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ManageSpending implements SpendingBudget {
    private final SpendingStore store;
    private final IdentityQuery identity;
    private final AuthorizationQuery authorization;
    private final DepartmentRepository departments;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ManageSpending(SpendingStore store, IdentityQuery identity,
                          AuthorizationQuery authorization, DepartmentRepository departments) {
        this(store, identity, authorization, departments, Clock.systemUTC());
    }
    ManageSpending(SpendingStore store, IdentityQuery identity, AuthorizationQuery authorization,
                   DepartmentRepository departments, Clock clock) {
        this.store = store;
        this.identity = identity;
        this.authorization = authorization;
        this.departments = departments;
        this.clock = clock;
    }
    public record Balance(String month, String timezone, BigDecimal limit, BigDecimal spent,
                          BigDecimal reserved, BigDecimal available, int alertPct, boolean alert,
                          boolean configured) {}
    public record DepartmentOption(UUID id, String name) {}
    public record Overview(String month, SpendingStore.Settings settings, SpendingPlan plan,
                           List<DepartmentOption> departments, List<UserContext> employees,
                           List<EmployeeBalance> balances, List<SpendingStore.Reservation> pending) {}
    public record EmployeeBalance(UUID userId, Balance balance) {}

    private void require(TenantId tenant, UserId actor, Resource resource) {
        if (!authorization.isAllowed(tenant, actor, resource, Action.MANAGE, new AccessTarget.TenantTarget())) {
            throw new SpendingDeniedException("SPENDING_FORBIDDEN", "Sem permissão para esta operação financeira.");
        }
    }
    private YearMonth month(SpendingStore.Settings settings) {
        return YearMonth.now(clock.withZone(ZoneId.of(settings.timezone())));
    }
    private List<UserContext> employees(TenantId tenant) {
        var activeDepartments = departments.findByTenantId(tenant).stream()
            .filter(d -> "ACTIVE".equals(d.status().name())).map(d -> d.id()).toList();
        return identity.eligibleUsers(tenant).stream().filter(u -> u.tenantId().equals(tenant)
            && "ACTIVE".equals(u.status()) && activeDepartments.contains(u.departmentId())).toList();
    }
    private SpendingPlan current(TenantId tenant, YearMonth month) {
        var found = store.plan(tenant.value(), month);
        if (found.isPresent()) return calculate(tenant, found.get());
        var previous = store.previous(tenant.value(), month);
        if (previous.isEmpty()) return null;
        SpendingPlan old = previous.get();
        var activeDepartments = departments.findByTenantId(tenant).stream()
            .filter(d -> "ACTIVE".equals(d.status().name())).map(d -> d.id()).toList();
        var shares = old.departments().stream().filter(d -> activeDepartments.contains(d.departmentId())).toList();
        SpendingPlan renewed = new SpendingPlan(old.amount(), shares, List.of(), old.weights(), old.rates());
        store.save(tenant.value(), month, renewed);
        // Autor de sistema representado pelo tenant, operação distinguida no histórico.
        store.audit(tenant.value(), tenant.value(), "MONTH_RENEWED", old, renewed);
        return calculate(tenant, renewed);
    }
    public Overview overview(TenantId tenant, UserId actor, YearMonth requested) {
        require(tenant, actor, Resource.SPENDING_BUDGET);
        var settings = store.lock(tenant.value());
        YearMonth selected = requested == null ? month(settings) : requested;
        SpendingPlan plan = selected.equals(month(settings)) ? current(tenant, selected)
            : store.plan(tenant.value(), selected).orElse(null);
        var employees = new ArrayList<>(employees(tenant));
        var balances = employees.stream().map(u -> new EmployeeBalance(u.userId().value(),
            balance(tenant, u.userId(), selected, settings, plan))).toList();
        var options = departments.findByTenantId(tenant).stream()
            .filter(d -> "ACTIVE".equals(d.status().name()) || (plan != null && plan.departments().stream()
                .anyMatch(share -> share.departmentId().equals(d.id()))))
            .map(d -> new DepartmentOption(d.id(), d.name() + ("ACTIVE".equals(d.status().name()) ? "" : " (inativo)"))).toList();
        return new Overview(selected.toString(), settings, plan, options, employees, balances, store.pending(tenant.value()));
    }
    public SpendingStore.Settings settings(TenantId tenant, UserId actor) {
        require(tenant, actor, Resource.SPENDING_ALERT);
        return store.lock(tenant.value());
    }
    public void alert(TenantId tenant, UserId actor, int pct) {
        require(tenant, actor, Resource.SPENDING_ALERT);
        if (pct < 1 || pct > 100) throw new IllegalArgumentException("Percentual deve estar entre 1 e 100.");
        var before = store.lock(tenant.value());
        var after = new SpendingStore.Settings(before.timezone(), pct);
        store.settings(tenant.value(), after);
        store.audit(tenant.value(), actor.value(), "ALERT_CHANGED", before, after);
    }
    public void timezone(TenantId tenant, UserId actor, String timezone) {
        require(tenant, actor, Resource.SPENDING_BUDGET);
        try { ZoneId.of(timezone); }
        catch (java.time.DateTimeException | NullPointerException ex) {
            throw new IllegalArgumentException("Fuso da empresa inválido.");
        }
        var before = store.lock(tenant.value());
        // Após criar um plano, trocar fuso deslocaria o período de cobrança em curso.
        if (!before.timezone().equals(timezone) && store.plan(tenant.value(), month(before)).isPresent()) {
            throw new IllegalArgumentException("Configure o fuso antes de criar o orçamento do mês.");
        }
        var after = new SpendingStore.Settings(timezone, before.alertPct());
        store.settings(tenant.value(), after);
        store.audit(tenant.value(), actor.value(), "TIMEZONE_CHANGED", before, after);
    }
    private void validateDepartments(TenantId tenant, SpendingPlan plan) {
        for (var share : plan.departments()) {
            var department = departments.findByTenantIdAndId(tenant, share.departmentId());
            if (department.isEmpty()) {
                throw new IllegalArgumentException("Departamento indisponível nesta empresa.");
            }
        }
    }
    public SpendingPlan suggest(TenantId tenant, UserId actor, SpendingPlan draft) {
        require(tenant, actor, Resource.SPENDING_BUDGET);
        var policy = policy(draft);
        validateDepartments(tenant, policy);
        return calculate(tenant, policy);
    }
    private SpendingPlan policy(SpendingPlan plan) {
        return new SpendingPlan(plan.amount(), plan.departments(), List.of(), plan.weights(), plan.rates());
    }
    private SpendingPlan calculate(TenantId tenant, SpendingPlan draft) {
        List<Allocation> allocations = new ArrayList<>();
        var active = employees(tenant);
        for (var share : draft.departments()) {
            var users = active.stream().filter(u -> share.departmentId().equals(u.departmentId()))
                .sorted(java.util.Comparator.comparing(u -> u.userId().value())).toList();
            BigDecimal totalWeight = users.stream().map(u -> draft.weights().get(u.hierarchyLevel()))
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (var u : users) {
                BigDecimal weight = draft.weights().get(u.hierarchyLevel());
                if (weight == null) throw new IllegalArgumentException("Defina peso para todos os cargos presentes.");
                BigDecimal limit = SpendingPlan.shareAmount(draft.amount(), share.percentage())
                    .multiply(weight).divide(totalWeight, 2, RoundingMode.DOWN);
                allocations.add(new Allocation(u.userId().value(), share.departmentId(), limit));
            }
        }
        return new SpendingPlan(draft.amount(), draft.departments(), allocations, draft.weights(), draft.rates());
    }
    public void save(TenantId tenant, UserId actor, YearMonth selected, SpendingPlan plan) {
        require(tenant, actor, Resource.SPENDING_BUDGET);
        var settings = store.lock(tenant.value());
        if (!selected.equals(month(settings))) throw new IllegalArgumentException("Edite somente o orçamento vigente.");
        SpendingPlan before = current(tenant, selected);
        SpendingPlan effective = policy(plan);
        validateDepartments(tenant, effective);
        var total = store.companyUsage(tenant.value(), selected);
        if (effective.amount().compareTo(total.spent().add(total.reserved())) < 0) {
            throw new IllegalArgumentException("Orçamento abaixo dos gastos e reservas da empresa.");
        }
        for (var department : departments.findByTenantId(tenant)) {
            var usage = store.departmentUsage(tenant.value(), selected, department.id());
            BigDecimal limit = effective.departments().stream().filter(d -> d.departmentId().equals(department.id()))
                .map(d -> SpendingPlan.shareAmount(effective.amount(), d.percentage())).findFirst().orElse(BigDecimal.ZERO);
            if (limit.compareTo(usage.spent().add(usage.reserved())) < 0) {
                throw new IllegalArgumentException("Parcela abaixo dos gastos e reservas do departamento.");
            }
        }
        store.save(tenant.value(), selected, effective);
        store.audit(tenant.value(), actor.value(), "PLAN_CHANGED", before, effective);
    }
    private Balance balance(TenantId tenant, UserId user, YearMonth month, SpendingStore.Settings settings, SpendingPlan plan) {
        var usage = store.usage(tenant.value(), month, user.value());
        var allocation = plan == null ? null : plan.allocations().stream()
            .filter(a -> a.userId().equals(user.value())).findFirst().orElse(null);
        BigDecimal limit = allocation == null ? BigDecimal.ZERO : allocation.limit();
        boolean alert = limit.signum() > 0 && usage.spent().multiply(BigDecimal.valueOf(100))
            .compareTo(limit.multiply(BigDecimal.valueOf(settings.alertPct()))) >= 0;
        BigDecimal available = limit.subtract(usage.spent()).subtract(usage.reserved());
        if (allocation != null) {
            var department = store.departmentUsage(tenant.value(), month, allocation.departmentId());
            var company = store.companyUsage(tenant.value(), month);
            BigDecimal departmentLimit = plan.departments().stream()
                .filter(d -> d.departmentId().equals(allocation.departmentId()))
                .map(d -> SpendingPlan.shareAmount(plan.amount(), d.percentage())).findFirst().orElse(BigDecimal.ZERO);
            available = available.min(departmentLimit.subtract(department.spent()).subtract(department.reserved()))
                .min(plan.amount().subtract(company.spent()).subtract(company.reserved()));
        }
        return new Balance(month.toString(), settings.timezone(), limit, usage.spent(), usage.reserved(),
            available, settings.alertPct(), alert, allocation != null);
    }
    public Balance own(TenantId tenant, UserId actor) {
        if (!authorization.isAllowed(tenant, actor, Resource.CHAT, Action.READ, new AccessTarget.OwnedTarget(actor, null))) {
            throw new SpendingDeniedException("SPENDING_FORBIDDEN", "Sem permissão para consultar saldo.");
        }
        var settings = store.lock(tenant.value());
        YearMonth selected = month(settings);
        return balance(tenant, actor, selected, settings, current(tenant, selected));
    }
    @Override public void reserve(TenantId tenant, UserId user, UUID model, UUID request, BigDecimal maximumCost) {
        var settings = store.lock(tenant.value());
        if (store.reservation(tenant.value(), request).isPresent()) {
            throw new SpendingDeniedException("SPENDING_REQUEST_DUPLICATE", "Esta execução já possui reserva.");
        }
        YearMonth selected = month(settings);
        SpendingPlan plan = current(tenant, selected);
        var context = identity.getUserContext(user).filter(u -> u.tenantId().equals(tenant) && "ACTIVE".equals(u.status()))
            .orElseThrow(() -> new SpendingDeniedException("SPENDING_FORBIDDEN", "Funcionário indisponível."));
        if (context.departmentId() == null || departments.findByTenantIdAndId(tenant, context.departmentId())
                .filter(d -> "ACTIVE".equals(d.status().name())).isEmpty()) {
            throw new SpendingDeniedException("SPENDING_FORBIDDEN", "Departamento indisponível.");
        }
        var allocation = plan == null ? null : plan.allocations().stream().filter(a -> a.userId().equals(user.value())
            && Objects.equals(a.departmentId(), context.departmentId())).findFirst().orElse(null);
        if (allocation == null) throw new SpendingDeniedException("SPENDING_NOT_CONFIGURED", "O orçamento do seu departamento ainda precisa ser configurado pelo executivo.");
        var rate = plan.rates().stream().filter(r -> r.modelId().equals(model)).findFirst()
            .orElseThrow(() -> new SpendingDeniedException("SPENDING_PRICE_MISSING", "A moeda e a taxa deste modelo precisam ser configuradas pelo executivo."));
        if (maximumCost == null || maximumCost.signum() < 0) throw new IllegalArgumentException("Reserva inválida.");
        BigDecimal reserve = maximumCost.multiply(rate.exchangeRate()).setScale(8, RoundingMode.UP);
        var balance = balance(tenant, user, selected, settings, plan);
        if (balance.available().signum() <= 0 || reserve.compareTo(balance.available()) > 0) {
            throw new SpendingDeniedException("SPENDING_INSUFFICIENT", "Saldo insuficiente para esta solicitação de IA.");
        }
        store.reserve(tenant.value(), new SpendingStore.Reservation(request, user.value(), selected,
            reserve, null, rate.exchangeRate(), rate.currency(), rate.reference(), "PENDING", clock.instant(), context.departmentId()));
    }
    @Override public void settle(TenantId tenant, UUID request, BigDecimal actualCost) {
        store.lock(tenant.value());
        var reservation = store.reservation(tenant.value(), request).orElseThrow();
        if (actualCost == null || actualCost.signum() < 0) throw new IllegalArgumentException("Custo inválido.");
        BigDecimal brl = actualCost.multiply(reservation.exchangeRate()).setScale(8, RoundingMode.HALF_UP);
        if ("SETTLED".equals(reservation.state())) {
            if (reservation.actual().compareTo(brl) >= 0) return;
            store.audit(tenant.value(), reservation.userId(), "LATE_COST_ADJUSTMENT", reservation,
                Map.of("actualBRL", brl, "requestId", request));
        }
        store.settle(tenant.value(), request, brl);
    }
    public void reconcile(TenantId tenant, UserId actor, UUID request, BigDecimal cost, String reason) {
        require(tenant, actor, Resource.SPENDING_BUDGET);
        if (reason == null || reason.isBlank() || reason.length() > 200) throw new IllegalArgumentException("Informe a referência da conciliação (até 200 caracteres).");
        store.lock(tenant.value());
        var reservation = store.reservation(tenant.value(), request)
            .orElseThrow(() -> new SpendingDeniedException("SPENDING_NOT_FOUND", "Reserva não encontrada."));
        if (!"PENDING".equals(reservation.state())) throw new IllegalArgumentException("Reserva já conciliada.");
        if (reservation.createdAt().isAfter(clock.instant().minusSeconds(600))) {
            throw new IllegalArgumentException("Aguarde 10 minutos para conciliar uma execução potencialmente em andamento.");
        }
        settle(tenant, request, cost);
        store.audit(tenant.value(), actor.value(), "RESERVATION_RECONCILED", reservation,
            Map.of("cost", cost, "reference", reason));
    }
}
