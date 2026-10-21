package org.sona.auth;

import static org.sona.auth.SonaJwtConverter.ISSUER;
import static org.sona.auth.SonaJwtConverter.ROLE_CLAIM;
import static org.sona.auth.SonaJwtConverter.STATE_CLAIM;
import static org.sona.auth.SonaJwtConverter.USERNAME_CLAIM;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.sona.model.enums.UserRole;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Signs access tokens as Sona does, for tests of what a token lets its user do.
 */
@RequiredArgsConstructor
public final class TestTokens {

    private final JwtEncoder encoder;

    public String accessToken(final UUID userId, final String username, final UserRole role, final SessionState state) {
        return accessToken(userId, username, role, state, _ -> {});
    }

    /**
     * @return an access token with the claims added or overridden
     */
    public String accessToken(
            final UUID userId,
            final String username,
            final UserRole role,
            final SessionState state,
            final Consumer<JwtClaimsSet.Builder> claims) {
        final var now = Instant.now();
        final var builder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(USERNAME_CLAIM, username)
                .claim(ROLE_CLAIM, role.getLiteral())
                .claim(STATE_CLAIM, state.name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        claims.accept(builder);
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build()))
                .getTokenValue();
    }
}
