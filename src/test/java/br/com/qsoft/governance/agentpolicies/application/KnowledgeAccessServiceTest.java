package br.com.qsoft.governance.agentpolicies.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSearchRepository;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A lotação vem do IdentityQuery; sem lotação só tenant_wide. */
class KnowledgeAccessServiceTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId userId = UserId.random();
    private final UUID departmentId = UUID.randomUUID();

    private final List<UUID> requestedDepartments = new ArrayList<>();

    private final KnowledgeSearchRepository repository =
        (tenant, department, query, limit) -> {
            requestedDepartments.add(department);
            return List.of(new KnowledgeHit(UUID.randomUUID(), "trecho", UUID.randomUUID(),
                "Título", 1, 0.5));
        };

    private final IdentityQuery identityQuery = new IdentityQuery() {
        @Override
        public Optional<UserContext> getUserContext(UserId id) {
            return Optional.of(new UserContext(id, tenantId, "COLLABORATOR", departmentId,
                "ACTIVE"));
        }
    };

    private final KnowledgeAccessService service =
        new KnowledgeAccessService(repository, identityQuery);

    @Test
    void usaALotacaoDoUsuario() {
        List<KnowledgeHit> hits = service.search(tenantId, userId, "política");

        assertThat(hits).hasSize(1);
        assertThat(requestedDepartments).containsExactly(departmentId);
    }

    @Test
    void usuarioSemLotacaoBuscaSemDepartamento() {
        IdentityQuery noDepartment = id -> Optional.of(
            new UserContext(id, tenantId, "COLLABORATOR", null, "ACTIVE"));
        KnowledgeAccessService without = new KnowledgeAccessService(repository, noDepartment);

        without.search(tenantId, userId, "política");

        assertThat(requestedDepartments).containsExactly((UUID) null);
    }

    @Test
    void contextoDeOutroTenantNaoVazaLotacao() {
        IdentityQuery otherTenant = id -> Optional.of(
            new UserContext(id, TenantId.random(), "COLLABORATOR", departmentId, "ACTIVE"));
        KnowledgeAccessService isolated = new KnowledgeAccessService(repository, otherTenant);

        isolated.search(tenantId, userId, "política");

        assertThat(requestedDepartments).containsExactly((UUID) null);
    }

    @Test
    void consultaVaziaNaoChamaRepositorio() {
        assertThat(service.search(tenantId, userId, "  ")).isEmpty();
        assertThat(requestedDepartments).isEmpty();
    }
}
