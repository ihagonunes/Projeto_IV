package br.com.qsoft.governance.agentpolicies.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.agentpolicies.application.port.DemoDirectoryLookup;
import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSeederRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Seed idempotente, com fallback tenant_wide quando o departamento não existe. */
class AgentPoliciesDemoSeederTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID marketingId = UUID.randomUUID();
    private final UUID engineeringId = UUID.randomUUID();

    private final Set<String> existing = new HashSet<>();
    private final List<SeedCall> calls = new ArrayList<>();

    private final KnowledgeSeederRepository repository = new KnowledgeSeederRepository() {
        @Override
        public boolean existsByTitle(TenantId tenant, String title) {
            return existing.contains(title);
        }

        @Override
        public void seed(TenantId tenant, String title, boolean tenantWide,
                         Set<UUID> departmentIds, List<String> chunks) {
            existing.add(title);
            calls.add(new SeedCall(title, tenantWide, departmentIds, chunks));
        }
    };

    private DemoDirectoryLookup directory(boolean tenant, boolean departments) {
        return new DemoDirectoryLookup() {
            @Override
            public Optional<UUID> findTenantIdBySlug(String slug) {
                return tenant ? Optional.of(tenantId) : Optional.empty();
            }

            @Override
            public Optional<UUID> findDepartmentIdByName(UUID tid, String name) {
                if (!departments) {
                    return Optional.empty();
                }
                if ("Marketing".equals(name)) {
                    return Optional.of(marketingId);
                }
                if ("Engenharia".equals(name)) {
                    return Optional.of(engineeringId);
                }
                return Optional.empty();
            }
        };
    }

    @Test
    void semTenantDemoNaoCriaDocumentos() {
        new AgentPoliciesDemoSeeder(repository, directory(false, false), "qsoft").seed();

        assertThat(calls).isEmpty();
    }

    @Test
    void criaQuatroDocumentosComTrechos() {
        new AgentPoliciesDemoSeeder(repository, directory(true, false), "qsoft").seed();

        assertThat(calls).extracting(SeedCall::title).containsExactly(
            AgentPoliciesDemoSeeder.POLICY_AI_USE,
            AgentPoliciesDemoSeeder.POLICY_LGPD,
            AgentPoliciesDemoSeeder.POLICY_MARKETING,
            AgentPoliciesDemoSeeder.POLICY_ENGINEERING);
        assertThat(calls).allSatisfy(call -> {
            assertThat(call.chunks()).isNotEmpty();
            assertThat(call.chunks()).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(800));
        });
        // Sem departamentos no seed, os documentos setoriais viram tenant_wide.
        assertThat(calls).allSatisfy(call -> assertThat(call.tenantWide()).isTrue());
    }

    @Test
    void quandoOsDepartamentosExistemAplicaOEscopo() {
        new AgentPoliciesDemoSeeder(repository, directory(true, true), "qsoft").seed();

        assertThat(calls).filteredOn(call -> call.title().equals(
                AgentPoliciesDemoSeeder.POLICY_MARKETING))
            .singleElement()
            .satisfies(call -> {
                assertThat(call.tenantWide()).isFalse();
                assertThat(call.departments()).containsExactly(marketingId);
            });
        assertThat(calls).filteredOn(call -> call.title().equals(
                AgentPoliciesDemoSeeder.POLICY_ENGINEERING))
            .singleElement()
            .satisfies(call -> {
                assertThat(call.tenantWide()).isFalse();
                assertThat(call.departments()).containsExactly(engineeringId);
            });
    }

    @Test
    void segundaExecucaoNaoDuplica() {
        AgentPoliciesDemoSeeder seeder =
            new AgentPoliciesDemoSeeder(repository, directory(true, false), "qsoft");
        seeder.seed();
        seeder.seed();

        assertThat(calls).hasSize(4);
    }

    private record SeedCall(String title, boolean tenantWide, Set<UUID> departments,
                            List<String> chunks) {
    }
}
