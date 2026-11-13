package br.com.qsoft.governance.engagement.infrastructure.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.iam.api.query.TenantQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class EngagementDemoSeederTest {

    private final TenantQuery tenantQuery = mock(TenantQuery.class);
    private final IdentityQuery identityQuery = mock(IdentityQuery.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EngagementDemoSeeder seeder = new EngagementDemoSeeder(tenantQuery, identityQuery, jdbc);
    private final TenantId tenantId = TenantId.random();
    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void skipsSeedingWhenDemoTenantDoesNotExist() {
        when(tenantQuery.findBySlug("qsoft")).thenReturn(Optional.empty());

        seeder.run(args);

        verify(tenantQuery).findBySlug("qsoft");
        verifyNoInteractions(identityQuery, jdbc);
    }

    @Test
    void skipsWritesWhenDemoTenantHasNoEligibleUsers() {
        when(tenantQuery.findBySlug("qsoft")).thenReturn(Optional.of(tenantId));
        when(identityQuery.eligibleUsers(tenantId)).thenReturn(List.of());

        seeder.run(args);

        verify(identityQuery).eligibleUsers(tenantId);
        verifyNoInteractions(jdbc);
    }

    @Test
    void seedsTrainingForBothUsersAndLicenseOnlyForSecondUser() {
        UserId firstUser = UserId.random();
        UserId secondUser = UserId.random();
        when(tenantQuery.findBySlug("qsoft")).thenReturn(Optional.of(tenantId));
        when(identityQuery.eligibleUsers(tenantId)).thenReturn(List.of(
            new UserContext(firstUser, tenantId, "COLLABORATOR", null, "ACTIVE", "First"),
            new UserContext(secondUser, tenantId, "COLLABORATOR", null, "ACTIVE", "Second")));

        seeder.run(args);

        verify(jdbc).update(startsWith("INSERT INTO engagement_training_records"),
            any(UUID.class), eq(tenantId.value()), eq(firstUser.value()), eq("COMPLETED"),
            any(LocalDate.class), any(Date.class));
        verify(jdbc).update(startsWith("INSERT INTO engagement_ai_licenses"),
            any(UUID.class), eq(tenantId.value()), eq(secondUser.value()), any(LocalDate.class));
        verify(jdbc).update(startsWith("INSERT INTO engagement_training_records"),
            any(UUID.class), eq(tenantId.value()), eq(secondUser.value()), eq("ENROLLED"),
            any(LocalDate.class), isNull());
        verifyNoMoreInteractions(jdbc);
    }
}
