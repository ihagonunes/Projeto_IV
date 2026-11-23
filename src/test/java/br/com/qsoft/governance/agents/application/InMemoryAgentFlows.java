package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Fluxos/versões em memória para os testes unitários do BE-03. */
final class InMemoryAgentFlows implements AgentFlowRepository, AgentFlowVersionRepository {

    private final List<AgentFlow> flows = new ArrayList<>();
    private final List<AgentFlowVersion> versions = new ArrayList<>();

    List<AgentFlow> flows() {
        return List.copyOf(flows);
    }

    List<AgentFlowVersion> versions() {
        return List.copyOf(versions);
    }

    @Override
    public AgentFlow save(AgentFlow flow) {
        Optional<AgentFlow> existing = flows.stream()
            .filter(item -> item.id().equals(flow.id()))
            .findFirst();
        if (existing.isPresent() && !existing.get().tenantId().equals(flow.tenantId())) {
            throw new IllegalStateException("Fluxo pertence a outro tenant.");
        }
        flows.removeIf(item -> item.id().equals(flow.id()));
        flows.add(flow);
        return flow;
    }

    @Override
    public Optional<AgentFlow> findById(TenantId tenantId, UUID flowId) {
        return flows.stream()
            .filter(flow -> flow.tenantId().equals(tenantId) && flow.id().equals(flowId))
            .filter(flow -> flow.deletedAt() == null)
            .findFirst();
    }

    @Override
    public List<AgentFlow> findByOwner(TenantId tenantId, UserId ownerUserId) {
        return flows.stream()
            .filter(flow -> flow.tenantId().equals(tenantId)
                && flow.ownerUserId().equals(ownerUserId)
                && flow.deletedAt() == null)
            .toList();
    }

    @Override
    public List<AgentFlow> findByDepartment(TenantId tenantId, UUID departmentId) {
        return flows.stream()
            .filter(flow -> flow.tenantId().equals(tenantId)
                && Objects.equals(flow.departmentId(), departmentId)
                && flow.deletedAt() == null)
            .toList();
    }

    @Override
    public PageResult<AgentFlow> findVisible(TenantId tenantId, VisibilityScope scope,
                                             AgentFlowStatus status, int page, int size) {
        List<AgentFlow> visible = flows.stream()
            .filter(flow -> flow.tenantId().equals(tenantId) && flow.deletedAt() == null)
            .filter(flow -> status == null || flow.status() == status)
            .filter(flow -> AgentFlowAccess.visible(scope, flow))
            .sorted(Comparator.comparing(AgentFlow::updatedAt).reversed())
            .toList();
        int from = Math.min(page * size, visible.size());
        int to = Math.min(from + size, visible.size());
        return new PageResult<>(visible.subList(from, to), visible.size(), page, size);
    }

    @Override
    public boolean markDeleted(TenantId tenantId, UUID flowId, Instant deletedAt) {
        Optional<AgentFlow> found = findById(tenantId, flowId);
        if (found.isEmpty()) {
            return false;
        }
        AgentFlow flow = found.get();
        save(new AgentFlow(flow.id(), flow.tenantId(), flow.ownerUserId(), flow.departmentId(),
            flow.name(), flow.status(), flow.currentVersionId(), flow.rowVersion() + 1,
            flow.createdAt(), deletedAt, deletedAt));
        return true;
    }

    @Override
    public boolean updateIfRowVersion(AgentFlow flow, long expectedRowVersion) {
        Optional<AgentFlow> found = findById(flow.tenantId(), flow.id());
        if (found.isEmpty() || found.get().rowVersion() != expectedRowVersion) {
            return false;
        }
        save(flow);
        return true;
    }

    @Override
    public AgentFlowVersion save(TenantId tenantId, AgentFlowVersion version) {
        if (findById(tenantId, version.flowId(), version.id()).isEmpty()
            && flows.stream().noneMatch(flow -> flow.tenantId().equals(tenantId)
                && flow.id().equals(version.flowId()))) {
            throw new IllegalStateException("Fluxo não pertence ao tenant informado.");
        }
        versions.add(version);
        return version;
    }

    @Override
    public List<AgentFlowVersion> findByFlow(TenantId tenantId, UUID flowId) {
        return versions.stream()
            .filter(version -> version.flowId().equals(flowId))
            .filter(version -> flows.stream().anyMatch(flow ->
                flow.tenantId().equals(tenantId) && flow.id().equals(flowId)))
            .sorted(Comparator.comparingInt(AgentFlowVersion::versionNumber).reversed())
            .toList();
    }

    @Override
    public Optional<AgentFlowVersion> find(TenantId tenantId, UUID flowId, int versionNumber) {
        return findByFlow(tenantId, flowId).stream()
            .filter(version -> version.versionNumber() == versionNumber)
            .findFirst();
    }

    @Override
    public Optional<AgentFlowVersion> findById(TenantId tenantId, UUID flowId, UUID versionId) {
        return findByFlow(tenantId, flowId).stream()
            .filter(version -> version.id().equals(versionId))
            .findFirst();
    }

    @Override
    public int nextVersionNumber(TenantId tenantId, UUID flowId) {
        return findByFlow(tenantId, flowId).stream()
            .mapToInt(AgentFlowVersion::versionNumber)
            .max()
            .orElse(0) + 1;
    }
}
