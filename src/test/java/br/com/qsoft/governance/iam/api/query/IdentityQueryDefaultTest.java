package br.com.qsoft.governance.iam.api.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentityQueryDefaultTest {

    @Test
    void eligibleUsersRejectsImplementationsThatOnlyProvideUserContext() {
        IdentityQuery identityQuery = userId -> Optional.empty();
        TenantId tenantId = TenantId.random();

        assertThatThrownBy(() -> identityQuery.eligibleUsers(tenantId))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
