package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.GovernanceApplication;
import br.com.qsoft.governance.agentpolicies.application.KnowledgeAccessService;
import br.com.qsoft.governance.agentpolicies.application.KnowledgeHit;
import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSeederRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.support.TestTenants;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Isolamento da recuperação (SPEC-05 §6): tenant, departamento e usuário sem
 * lotação; nenhuma correspondência não inventa resultado.
 */
@Testcontainers
@SpringBootTest(classes = GovernanceApplication.class)
class KnowledgeAccessIT {

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

    private static final String PASSWORD = "secret123";
    private static final UUID DEPT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String DEPT_B_TEXT = "A política do departamento B é confidencial "
        + "e não pode ser lida por outro departamento.";

    @Autowired
    KnowledgeAccessService service;

    @Autowired
    KnowledgeSeederRepository seeder;

    @Autowired
    TestTenants testTenants;

    private Tenant tenantA;
    private Tenant tenantB;
    private User adminA;
    private User adminB;
    private User memberA;

    @BeforeEach
    void setUp() {
        tenantA = testTenants.ensureTenant("knowna", "Knowledge A", "Admin",
            "admin@knowna.com", PASSWORD);
        tenantB = testTenants.ensureTenant("knowb", "Knowledge B", "Admin",
            "admin@knowb.com", PASSWORD);
        adminA = testTenants.admin(tenantA, "admin@knowna.com");
        adminB = testTenants.admin(tenantB, "admin@knowb.com");
        memberA = testTenants.ensureMember(tenantA, adminA.id(), "member@knowna.com", "Member",
            PASSWORD, HierarchyLevel.COLLABORATOR, DEPT_A, "COLLABORATOR");

        seeder.seed(tenantA.id(), "Visão geral A", true, Set.of(),
            List.of("A política de orçamento de IA do tenant é definida pelo comitê."));
        seeder.seed(tenantA.id(), "Política do Depto A", false, Set.of(DEPT_A),
            List.of("A política do departamento A exige aprovação do gestor para o orçamento."));
        seeder.seed(tenantA.id(), "Política do Depto B", false, Set.of(DEPT_B),
            List.of(DEPT_B_TEXT));
        seeder.seed(tenantB.id(), "Visão geral B", true, Set.of(),
            List.of("A política do tenant B não aparece para usuários do tenant A."));
    }

    @Test
    void isolamentoDepartamental() {
        List<KnowledgeHit> hits = search(tenantA.id(), memberA.id(), "política");

        assertThat(hits).extracting(KnowledgeHit::title)
            .contains("Visão geral A", "Política do Depto A")
            .doesNotContain("Política do Depto B", "Visão geral B");
        assertThat(hits).noneMatch(hit -> hit.text().contains(DEPT_B_TEXT));
    }

    @Test
    void usuarioSemLotacaoVeApenasTenantWide() {
        List<KnowledgeHit> hits = search(tenantA.id(), adminA.id(), "política");

        assertThat(hits).extracting(KnowledgeHit::title).containsExactly("Visão geral A");
    }

    @Test
    void isolamentoDeTenant() {
        List<KnowledgeHit> hits = search(tenantB.id(), adminB.id(), "política");

        assertThat(hits).extracting(KnowledgeHit::title).containsExactly("Visão geral B");
    }

    @Test
    void semCorrespondenciaNaoInventaResultado() {
        assertThat(search(tenantA.id(), memberA.id(), "zebraquark")).isEmpty();
    }

    @Test
    void seedEhIdempotentePorTitulo() {
        seeder.seed(tenantA.id(), "Visão geral A", true, Set.of(),
            List.of("Outro texto que não deve substituir o original."));

        List<KnowledgeHit> hits = search(tenantA.id(), adminA.id(), "política");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).text()).contains("definida pelo comitê");
    }

    /** Simula o request autenticado, de onde o IdentityQuery lê o contexto. */
    private List<KnowledgeHit> search(TenantId tenantId, UserId userId, String query) {
        TenantContextHolder.set(new TenantContext(tenantId, userId));
        try {
            return service.search(tenantId, userId, query);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
