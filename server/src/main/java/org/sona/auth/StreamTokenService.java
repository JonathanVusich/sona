package org.sona.auth;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.config.properties.AuthProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Tokens that let a client stream one track by its URL, as an {@code <audio>} element can't send an
 * {@code Authorization} header. They have their own issuer, so they are never accepted as access tokens, and only
 * users who may read the library are given one, so a token is all the stream endpoint checks.
 */
@Component
@RequiredArgsConstructor
public final class StreamTokenService {

    public static final String ISSUER = "sona-stream";
    public static final String TRACK_CLAIM = "track";

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public String issueToken(final UUID userId, final UUID trackId) {
        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(TRACK_CLAIM, trackId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.streamTokenLifetime()))
                .build();
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
