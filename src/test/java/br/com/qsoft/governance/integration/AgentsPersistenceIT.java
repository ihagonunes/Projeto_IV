package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.agents.application.port.AgentCatalogOverrideRepository;
import br.com.qsoft.governance.agents.application.port.AgentCatalogRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentDefinition;
import br.com.qsoft.governance.agents.domain.AgentDefinitionOverride;
import br.com.qsoft.governance.agents.domain.AgentDefinitionStatus;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persistência e isolação por tenant do módulo agents (V027): catálogo global,
 * overrides e fluxos/versões. Tenant A nunca lê nem altera dados do Tenant B.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class AgentsPersistenceIT {

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

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Autowired
    AgentCatalogRepository catalogRepository;

    @Autowired
    AgentCatalogOverrideRepository overrideRepository;

    @Autowired
    AgentFlowRepository flowRepository;

    @Autowired
    AgentFlowVersionRepository versionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void catalogIsGlobalAndReadable() {
        String key = uniqueKey();
        insertCatalog(key, 1, "ACTIVE");
        insertCatalog(key, 2, "DRAFT");

        List<AgentDefinition> all = catalogRepository.findAll().stream()
            .filter(definition -> definition.agentKey().equals(key))
            .toList();

        assertThat(all).extracting(AgentDefinition::version).containsExactly(2, 1);
        assertThat(catalogRepository.findByAgentKey(key, 1))
            .get()
            .extracting(AgentDefinition::status)
            .isEqualTo(AgentDefinitionStatus.ACTIVE);
    }

    @Test
    void overrideIsVisibleOnlyInsideItsTenant() {
        String key = uniqueKey();
        insertCatalog(key, 1, "ACTIVE");
        TenantId tenantA = TenantId.random();
        TenantId tenantB = TenantId.random();

        overrideRepository.save(new AgentDefinitionOverride(tenantA, key, 1,
            "Prompt A", null, null, null, AgentDefinitionStatus.ACTIVE, NOW));

        assertThat(overrideRepository.find(tenantA, key, 1))
            .get()
            .extracting(AgentDefinitionOverride::promptTemplate)
            .isEqualTo("Prompt A");
        assertThat(overrideRepository.find(tenantB, key, 1)).isEmpty();
        assertThat(overrideRepository.findByTenant(tenantB)).isEmpty();
    }

    @Test
    void overrideUpsertTouchesOnlyTheOwningTenant() {
        String key = uniqueKey();
        insertCatalog(key, 1, "ACTIVE");
        TenantId tenantA = TenantId.random();
        TenantId tenantB = TenantId.random();

        overrideRepository.save(new AgentDefinitionOverride(tenantA, key, 1,
            "A1", null, null, null, null, NOW));
        overrideRepository.save(new AgentDefinitionOverride(tenantB, key, 1,
            "B1", null, null, null, null, NOW));
        overrideRepository.save(new AgentDefinitionOverride(tenantA, key, 1,
            "A2", null, null, null, AgentDefinitionStatus.ARCHIVED, NOW));

        assertThat(overrideRepository.find(tenantA, key, 1)).get()
            .satisfies(override -> {
                assertThat(override.promptTemplate()).isEqualTo("A2");
                assertThat(override.status()).isEqualTo(AgentDefinitionStatus.ARCHIVED);
            });
        assertThat(overrideRepository.find(tenantB, key, 1)).get()
            .extracting(AgentDefinitionOverride::promptTemplate)
            .isEqualTo("B1");
    }

    @Test
    void overrideDeleteIsTenantScoped() {
        String key = uniqueKey();
        insertCatalog(key, 1, "ACTIVE");
        TenantId tenantA = TenantId.random();
        overrideRepository.save(AgentDefinitionOverride.inheriting(tenantA, key, 1, NOW));

        assertThat(overrideRepository.delete(TenantId.random(), key, 1)).isFalse();
        assertThat(overrideRepository.find(tenantA, key, 1)).isPresent();
        assertThat(overrideRepository.delete(tenantA, key, 1)).isTrue();
        assertThat(overrideRepository.find(tenantA, key, 1)).isEmpty();
    }

    @Test
    void flowIsInvisibleToAnotherTenant() {
        TenantId tenantA = TenantId.random();
        TenantId tenantB = TenantId.random();
        UserId owner = UserId.random();
        AgentFlow flow = flowRepository.save(
            AgentFlow.create(UUID.randomUUID(), tenantA, owner, null, "Fluxo A", NOW));

        assertThat(flowRepository.findById(tenantA, flow.id())).isPresent();
        // Recurso de outro tenant é tratado como inexistente (404).
        assertThat(flowRepository.findById(tenantB, flow.id())).isEmpty();
        assertThat(flowRepository.findByOwner(tenantB, owner)).isEmpty();
        assertThat(flowRepository.findByOwner(tenantA, owner)).extracting(AgentFlow::id)
            .containsExactly(flow.id());
    }

    @Test
    void crossTenantSaveIsRejectedAndLeavesOriginalUntouched() {
        TenantId tenantA = TenantId.random();
        AgentFlow original = flowRepository.save(AgentFlow.create(
            UUID.randomUUID(), tenantA, UserId.random(), null, "Original", NOW));

        AgentFlow intrusion = new AgentFlow(original.id(), TenantId.random(),
            UserId.random(), null, "Intruso", AgentFlowStatus.DRAFT, null, 2L, NOW, NOW, null);

        assertThatThrownBy(() -> flowRepository.save(intrusion))
            .isInstanceOf(IllegalStateException.class);
        assertThat(flowRepository.findById(tenantA, original.id()))
            .get()
            .extracting(AgentFlow::name)
            .isEqualTo("Original");
    }

    @Test
    void markDeletedHidesFlowFromReads() {
        TenantId tenant = TenantId.random();
        AgentFlow flow = flowRepository.save(
            AgentFlow.create(UUID.randomUUID(), tenant, UserId.random(), null, "Some", NOW));

        assertThat(flowRepository.markDeleted(tenant, flow.id(), NOW)).isTrue();
        assertThat(flowRepository.findById(tenant, flow.id())).isEmpty();
        assertThat(flowRepository.markDeleted(tenant, flow.id(), NOW)).isFalse();
    }

    @Test
    void findByDepartmentIsTenantScoped() {
        TenantId tenantA = TenantId.random();
        TenantId tenantB = TenantId.random();
        UUID department = UUID.randomUUID();

        AgentFlow a = flowRepository.save(AgentFlow.create(
            UUID.randomUUID(), tenantA, UserId.random(), department, "Depto A", NOW));
        flowRepository.save(AgentFlow.create(
            UUID.randomUUID(), tenantB, UserId.random(), department, "Depto B", NOW));

        assertThat(flowRepository.findByDepartment(tenantA, department))
            .extracting(AgentFlow::id)
            .containsExactly(a.id());
    }

    @Test
    void versionsAreScopedByTheFlowTenant() {
        TenantId tenantA = TenantId.random();
        TenantId tenantB = TenantId.random();
        AgentFlow flow = flowRepository.save(
            AgentFlow.create(UUID.randomUUID(), tenantA, UserId.random(), null, "Fluxo", NOW));

        versionRepository.save(tenantA, new AgentFlowVersion(UUID.randomUUID(), flow.id(),
            1, 1, "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}",
            AgentValidationStatus.VALID, flow.ownerUserId(), NOW));
        versionRepository.save(tenantA, new AgentFlowVersion(UUID.randomUUID(), flow.id(),
            2, 1, "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}",
            AgentValidationStatus.INVALID, flow.ownerUserId(), NOW));

        assertThat(versionRepository.findByFlow(tenantA, flow.id()))
            .extracting(AgentFlowVersion::versionNumber)
            .containsExactly(2, 1);
        assertThat(versionRepository.find(tenantA, flow.id(), 2))
            .get()
            .extracting(AgentFlowVersion::validationStatus)
            .isEqualTo(AgentValidationStatus.INVALID);
        assertThat(versionRepository.find(tenantB, flow.id(), 1)).isEmpty();
        assertThat(versionRepository.findByFlow(tenantB, flow.id())).isEmpty();
    }

    @Test
    void versionWriteRequiresTheOwningTenant() {
        AgentFlow flow = flowRepository.save(AgentFlow.create(
            UUID.randomUUID(), TenantId.random(), UserId.random(), null, "Fluxo", NOW));

        AgentFlowVersion version = new AgentFlowVersion(UUID.randomUUID(), flow.id(),
            1, 1, "{}", AgentValidationStatus.VALID, flow.ownerUserId(), NOW);

        assertThatThrownBy(() -> versionRepository.save(TenantId.random(), version))
            .isInstanceOf(IllegalStateException.class);
        assertThat(versionRepository.find(flow.tenantId(), flow.id(), 1)).isEmpty();
    }

    private static String uniqueKey() {
        return "agent_" + UUID.randomUUID();
    }

    private void insertCatalog(String key, int version, String status) {
        jdbcTemplate.update("""
            INSERT INTO agents_catalog
                (id, agent_key, version, name, description, category, prompt_template,
                 tools_json, knowledge_scope, model_policy, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb),
                    ?, now(), now())
            """, UUID.randomUUID(), key, version, "Nome", "Descrição", "cat", "prompt",
            "[]", "{}", "{}", status);
    }
}
