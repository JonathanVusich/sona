package org.sona.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.sona.model.enums.UserRole;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Short-lived tokens that let a user who signed in through an OIDC provider, but has no account yet, create one. They
 * have their own issuer, so they are never accepted as access tokens.
 */
@Component
public final class RegistrationTokenService {

    private static final String ISSUER = "sona-registration";
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final String PROVIDER_CLAIM = "provider";
    private static final String OIDC_ISSUER_CLAIM = "oidc_issuer";
    private static final String OIDC_SUBJECT_CLAIM = "oidc_subject";
    private static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public RegistrationTokenService(final JwtEncoder encoder, final RSAKey signingKey) throws JOSEException {
        this.encoder = encoder;
        final var decoder =
                NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = decoder;
    }

    public String issueToken(final ProviderUser user) {
        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.userId().toString())
                .claim(PROVIDER_CLAIM, user.providerId())
                .claim(OIDC_ISSUER_CLAIM, user.issuer())
                .claim(OIDC_SUBJECT_CLAIM, user.subject())
                .claim(ROLE_CLAIM, user.role().getLiteral())
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * @return the user the token was issued for, or empty if it is invalid or expired
     */
    public Optional<ProviderUser> readToken(final String token) {
        try {
            final var jwt = decoder.decode(token);
            final var user = new ProviderUser(
                    jwt.getClaimAsString(PROVIDER_CLAIM),
                    jwt.getClaimAsString(OIDC_ISSUER_CLAIM),
                    jwt.getClaimAsString(OIDC_SUBJECT_CLAIM),
                    parseRole(jwt.getClaimAsString(ROLE_CLAIM)));
            return Optional.of(user);
        } catch (final JwtException _) {
            return Optional.empty();
        }
    }

    private static UserRole parseRole(final String claim) {
        return switch (claim) {
            case "ADMIN" -> UserRole.ADMIN;
            case "USER" -> UserRole.USER;
            case null, default -> throw new JwtException("Unknown role " + claim);
        };
    }
}
