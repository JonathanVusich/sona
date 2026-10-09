package org.sona.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * An OIDC provider served by WireMock, with its own signing key, that mints access tokens for tests.
 */
public final class FakeOidcProvider {

    public static final String AUDIENCE = "sona-test";
    public static final String ADMIN_GROUP = "sona-admins";

    private final WireMockServer server;
    private final RSAKey signingKey;
    private final JwtEncoder encoder;

    public FakeOidcProvider() throws JOSEException {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        signingKey = new RSAKeyGenerator(2048).keyID("fake-oidc").generate();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        stubDiscovery();
    }

    public String issuer() {
        return server.baseUrl();
    }

    /**
     * @return an access token from this provider for Sona, with the claims added or overridden
     */
    public String token(final Consumer<JwtClaimsSet.Builder> claims) {
        final var now = Instant.now();
        final var builder = JwtClaimsSet.builder()
                .issuer(issuer())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        claims.accept(builder);
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    public void stop() {
        server.stop();
    }

    private void stubDiscovery() {
        final var configuration = """
                {"issuer": "%s", "jwks_uri": "%s/jwks"}""".formatted(issuer(), issuer());
        server.stubFor(get("/.well-known/openid-configuration").willReturn(okJson(configuration)));
        final var keys = new JWKSet(signingKey.toPublicJWK());
        server.stubFor(get("/jwks").willReturn(okJson(keys.toString())));
    }
}
