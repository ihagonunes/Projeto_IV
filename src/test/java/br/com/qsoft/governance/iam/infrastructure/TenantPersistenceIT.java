package br.com.qsoft.governance.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração com PostgreSQL real (Testcontainers) + Flyway:
 * valida a migration V001, o mapeamento JPA (ddl-auto=validate) e a
 * persistência do Tenant por meio do caso de uso e do repositório.
 */
@Testcontainers
@SpringBootTest(classes = br.com.qsoft.governance.GovernanceApplication.class)
class TenantPersistenceIT {

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
    RegisterTenant registerTenant;

    @Autowired
    TenantRepository tenantRepository;

    @Test
    void shouldPersistTenant() {
        String slug = "acme-" + UUID.randomUUID();

        Tenant tenant = registerTenant.register(new RegisterTenantCommand(slug, "ACME", slug + ".example"));

        assertThat(tenant.id()).isNotNull();
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);

        Tenant found = tenantRepository.findBySlug(slug).orElseThrow();
        assertThat(found.id()).isEqualTo(tenant.id());
        assertThat(found.slug()).isEqualTo(slug);
        assertThat(found.name()).isEqualTo("ACME");
        assertThat(found.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(found.emailDomain()).isEqualTo(slug + ".example");
    }

    /** SPI-54: o domínio de e-mail corporativo é persistido e único globalmente. */
    @Test
    void shouldRejectDuplicateEmailDomain() {
        String slug = "dom-" + UUID.randomUUID();
        String domain = slug + ".example";
        registerTenant.register(new RegisterTenantCommand(slug, "Dom", domain));

        String otherSlug = "dom2-" + UUID.randomUUID();
        assertThatThrownBy(() -> registerTenant.register(
            new RegisterTenantCommand(otherSlug, "Dom2", domain)))
            .isInstanceOf(TenantDomainAlreadyExistsException.class);
    }

    @Test
    void shouldRejectDuplicateSlug() {
        String slug = "dup-" + UUID.randomUUID();
        registerTenant.register(new RegisterTenantCommand(slug, "Dup", slug + ".example"));

        assertThatThrownBy(() -> registerTenant.register(new RegisterTenantCommand(slug, "Dup2", slug + ".example")))
            .isInstanceOf(TenantSlugAlreadyExistsException.class);
    }

    @Test
    void shouldReturnEmptyForUnknownSlug() {
        assertThat(tenantRepository.findBySlug("unknown-" + UUID.randomUUID())).isEmpty();
    }
}
