package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.agents.application.port.AgentFlowExecutionRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.agents.domain.ExecutionStatus;
import br.com.qsoft.governance.agents.domain.FlowExecution;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BE-06/BE-07: concorrência real no PostgreSQL. Garante que o 412/unicidade não
 * depende de teste sequencial: duas escritas simultâneas, uma vence.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsFlowConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18.6-alpine")
            .withDatabaseName("qsoft_test")
            .withUsername("qsoft")
            .withPassword("qsoft_test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired
    AgentFlowRepository flows;

    @Autowired
    AgentFlowVersionRepository versions;

    @Autowired
    AgentFlowExecutionRepository executions;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void onlyOneConcurrentUpdateWinsTheRowVersion() throws Exception {
        TenantId tenant = TenantId.random();
        UserId owner = UserId.random();
        AgentFlow flow = flows.save(AgentFlow.create(UUID.randomUUID(), tenant, owner, null,
            "Race", NOW));
        AgentFlow left = replace(flow, "Left");
        AgentFlow right = replace(flow, "Right");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(update(barrier, left));
            Future<Boolean> second = pool.submit(update(barrier, right));

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        AgentFlow reloaded = flows.findById(tenant, flow.id()).orElseThrow();
        assertThat(reloaded.rowVersion()).isEqualTo(2L);
        assertThat(reloaded.name()).isIn("Left", "Right");
    }

    @Test
    void onlyOneConcurrentVersionNumberIsInserted() throws Exception {
        TenantId tenant = TenantId.random();
        UserId owner = UserId.random();
        AgentFlow flow = flows.save(AgentFlow.create(UUID.randomUUID(), tenant, owner, null,
            "Race version", NOW));
        AgentFlowVersion left = version(flow, 2);
        AgentFlowVersion right = version(flow, 2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(insertVersion(barrier, tenant, left));
            Future<Boolean> second = pool.submit(insertVersion(barrier, tenant, right));

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("""
            SELECT count(*) FROM agents_flow_versions WHERE flow_id = ? AND version_number = 2
            """, Integer.class, flow.id())).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeySimultaneouslyInsertsOnlyOneExecution() throws Exception {
        TenantId tenant = TenantId.random();
        UserId owner = UserId.random();
        AgentFlow flow = flows.save(AgentFlow.create(UUID.randomUUID(), tenant, owner, null,
            "Race idempotency", NOW));
        AgentFlowVersion version = versions.save(tenant, version(flow, 1));
        FlowExecution left = execution(tenant, owner, flow, version, "race-key");
        FlowExecution right = execution(tenant, owner, flow, version, "race-key");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(insertExecution(barrier, left));
            Future<Boolean> second = pool.submit(insertExecution(barrier, right));

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(executions.findByIdempotencyKey(tenant, owner, "race-key")).isPresent();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT count(*) FROM agents_flow_executions
             WHERE tenant_id = ? AND owner_user_id = ? AND idempotency_key = ?
            """, Integer.class, tenant.value(), owner.value(), "race-key")).isEqualTo(1);
    }

    private Callable<Boolean> update(CyclicBarrier barrier, AgentFlow flow) {
        return () -> {
            barrier.await();
            return flows.updateIfRowVersion(flow, 1L);
        };
    }

    private Callable<Boolean> insertVersion(CyclicBarrier barrier, TenantId tenant,
                                            AgentFlowVersion version) {
        return () -> {
            barrier.await();
            try {
                versions.save(tenant, version);
                return true;
            } catch (DataIntegrityViolationException ex) {
                return false;
            }
        };
    }

    private Callable<Boolean> insertExecution(CyclicBarrier barrier, FlowExecution execution) {
        return () -> {
            barrier.await();
            return executions.insertIfAbsent(execution);
        };
    }

    private static AgentFlow replace(AgentFlow flow, String name) {
        return new AgentFlow(flow.id(), flow.tenantId(), flow.ownerUserId(), flow.departmentId(),
            name, AgentFlowStatus.DRAFT, null, 2L, flow.createdAt(), NOW, null);
    }

    private static AgentFlowVersion version(AgentFlow flow, int number) {
        return new AgentFlowVersion(UUID.randomUUID(), flow.id(), number, 1, "{}",
            AgentValidationStatus.INVALID, flow.ownerUserId(), NOW);
    }

    private static FlowExecution execution(TenantId tenant, UserId owner, AgentFlow flow,
                                           AgentFlowVersion version, String key) {
        return new FlowExecution(UUID.randomUUID(), tenant, flow.id(), version.id(), owner,
            ExecutionStatus.RUNNING, key, null, null, NOW, null, NOW);
    }
}
