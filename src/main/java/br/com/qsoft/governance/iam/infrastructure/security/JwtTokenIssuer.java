package br.com.qsoft.governance.iam.infrastructure.security;

import br.com.qsoft.governance.iam.application.AuthenticatedUser;
import br.com.qsoft.governance.iam.application.port.TokenIssuer;
import br.com.qsoft.governance.platform.config.JwtSecurityProperties;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

/**
 * Emite JWT Bearer stateless com claims estáveis (sub=UserId, tenant_id)
 * e TTL configurado (ADR-30). Não embute permissões mutáveis no token.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtSecurityProperties properties;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, JwtSecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getTtlMinutes() * 60);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(user.userId().value().toString())
            .claim("tenant_id", user.tenantId().value().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
            .type("JWT")
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
