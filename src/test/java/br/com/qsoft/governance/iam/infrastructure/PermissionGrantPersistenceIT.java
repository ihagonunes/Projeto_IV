package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.domain.permission.Action;
import br.com.qsoft.governance.iam.domain.permission.PermissionGrant;
import br.com.qsoft.governance.iam.domain.permission.Resource;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.infrastructure.persistence.PermissionGrantJpaMapper;
import br.com.qsoft.governance.iam.infrastructure.persistence.PermissionGrantJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Teste de integração da matriz de permissões (POPROJ-52) com PostgreSQL real
 * (Testcontainers) + Flyway V005: valida o mapeamento JPA (ddl-auto=validate),
 * a persistência da tupla, a consulta por (roleLevel, resource, action) e a
 * unicidade garantida pela constraint UNIQUE. A matriz é global: a tabela não
 * possui tenant_id nem department_id.
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class PermissionGrantPersistenceIT {
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

    @Autowired
    PermissionGrantJpaRepository jpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final PermissionGrantJpaMapper mapper = new PermissionGrantJpaMapper();

    private PermissionGrant grant(HierarchyLevel roleLevel, Resource resource, Action action) {
        return new PermissionGrant(UUID.randomUUID(), roleLevel, resource, action);
    }

    @Test
    void shouldPersistAndQueryByRoleLevelResourceAction() {
        PermissionGrant grant = grant(HierarchyLevel.MANAGER, Resource.MODEL, Action.WRITE);

        jpaRepository.save(mapper.toEntity(grant));

        var found = jpaRepository.findByRoleLevelAndResourceAndAction(
            HierarchyLevel.MANAGER, Resource.MODEL, Action.WRITE).orElseThrow();
        assertThat(found.getId()).isEqualTo(grant.id());
        assertThat(found.getRoleLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(found.getResource()).isEqualTo(Resource.MODEL);
        assertThat(found.getAction()).isEqualTo(Action.WRITE);
    }

    @Test
    void shouldCreateGlobalMatrixWithoutTenantOrDepartment() {
        // A matriz é a mesma para todos os tenants: a tabela deve ter apenas as
        // colunas id, role_level, resource e action.
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'permission_grant'");

        List<String> names = columns.stream()
            .map(row -> String.valueOf(row.get("column_name")))
            .toList();
        assertThat(names).containsExactlyInAnyOrder("id", "role_level", "resource", "action");
    }

    @Test
    void shouldRejectDuplicateTuple() {
        PermissionGrant first = grant(HierarchyLevel.COLLABORATOR, Resource.USER, Action.READ);
        jpaRepository.save(mapper.toEntity(first));

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(
            mapper.toEntity(grant(HierarchyLevel.COLLABORATOR, Resource.USER, Action.READ))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
