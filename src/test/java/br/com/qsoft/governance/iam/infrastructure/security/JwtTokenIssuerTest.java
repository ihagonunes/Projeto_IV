package br.com.qsoft.governance.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.AuthenticatedUser;
import br.com.qsoft.governance.platform.config.JwtSecurityProperties;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789";
    private static final long TTL_MINUTES = 30;

    private SecretKey secretKey() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private JwtEncoder encoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    private JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).build();
    }

    private JwtTokenIssuer issuer() {
        JwtSecurityProperties props = new JwtSecurityProperties();
        props.setSecret(SECRET);
        props.setTtlMinutes(TTL_MINUTES);
        return new JwtTokenIssuer(encoder(), props);
    }

    @Test
    void shouldIssueTokenWithStableClaimsOnly() {
        UserId userId = UserId.random();
        TenantId tenantId = TenantId.random();

        String token = issuer().issue(new AuthenticatedUser(userId, tenantId, "ana@x.com", "MANAGER"));

        Jwt jwt = decoder().decode(token);
        assertThat(jwt.getSubject()).isEqualTo(userId.value().toString());
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(tenantId.value().toString());
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
    }

    @Test
    void shouldSetExpiryAroundConfiguredTtl() {
        UserId userId = UserId.random();
        TenantId tenantId = TenantId.random();

        String token = issuer().issue(new AuthenticatedUser(userId, tenantId, "ana@x.com", "MANAGER"));
        Jwt jwt = decoder().decode(token);

        Instant expectedExpiry = Instant.now().plusSeconds(TTL_MINUTES * 60);
        assertThat(jwt.getExpiresAt())
            .isBetween(expectedExpiry.minusSeconds(60), expectedExpiry.plusSeconds(60));
    }
}
