package br.com.qsoft.governance.engagement.infrastructure.bootstrap;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.iam.api.query.TenantQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seed de demonstração do engajamento (SPI-145): licenças e treinamentos
 * idempotentes para os usuários do tenant demo. O uso (dias ativos) vem das
 * chamadas reais ao chat/agente; o seeder não escreve em tabelas de outro módulo.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.bootstrap.demo-data", havingValue = "true")
public class EngagementDemoSeeder implements ApplicationRunner {

    static final String DEMO_SLUG = "qsoft";

    private final TenantQuery tenantQuery;
    private final IdentityQuery identityQuery;
    private final JdbcTemplate jdbcTemplate;

    public EngagementDemoSeeder(TenantQuery tenantQuery, IdentityQuery identityQuery,
                                JdbcTemplate jdbcTemplate) {
        this.tenantQuery = tenantQuery;
        this.identityQuery = identityQuery;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantId tenantId = tenantQuery.findBySlug(DEMO_SLUG).orElse(null);
        if (tenantId == null) {
            return;
        }
        List<UserContext> users = identityQuery.eligibleUsers(tenantId);
        if (users.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int index = 0; index < users.size(); index++) {
            UserContext user = users.get(index);
            if (index % 3 != 0) {
                seedLicense(tenantId, user.userId(), today);
            }
            seedTraining(tenantId, user.userId(), index, today);
        }
    }

    private void seedLicense(TenantId tenantId, br.com.qsoft.governance.sharedkernel.id.UserId userId,
                             LocalDate today) {
        UUID id = UUID.nameUUIDFromBytes(("engagement-demo-license:"
            + tenantId.value() + ":" + userId.value()).getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("""
            INSERT INTO engagement_ai_licenses
                (id, tenant_id, user_id, ai_tool_id, license_type, status,
                 assigned_at, expires_at, created_at)
            VALUES (?, ?, ?, NULL, 'DEMO', 'ACTIVE', ?, NULL, now())
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId.value(), userId.value(), today.minusDays(60));
    }

    private void seedTraining(TenantId tenantId,
                              br.com.qsoft.governance.sharedkernel.id.UserId userId,
                              int index, LocalDate today) {
        UUID id = UUID.nameUUIDFromBytes(("engagement-demo-training:"
            + tenantId.value() + ":" + userId.value()).getBytes(StandardCharsets.UTF_8));
        boolean completed = index % 2 == 0;
        jdbcTemplate.update("""
            INSERT INTO engagement_training_records
                (id, tenant_id, user_id, course_name, course_category, status,
                 enrolled_at, completed_at, created_at)
            VALUES (?, ?, ?, 'Governança de IA', 'DEMO', ?, ?, ?, now())
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId.value(), userId.value(), completed ? "COMPLETED" : "ENROLLED",
            today.minusDays(90), completed ? java.sql.Date.valueOf(today.minusDays(5)) : null);
    }
}
