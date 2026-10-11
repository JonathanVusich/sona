package org.sona.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * An OIDC provider served by WireMock, with its own signing key. Tests tell it which authorization codes to accept,
 * and it answers Sona's token requests with ID tokens it signs.
 */
public final class FakeOidcProvider {

    public static final String ID = "fake";
    public static final String NAME = "Fake";
    public static final String CLIENT_ID = "sona-test";
    public static final String CLIENT_SECRET = "sona-test-secret";
    public static final String ADMIN_GROUP = "sona-admins";

    private static final String TOKEN_PATH = "/token";

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
     * @return an ID token for Sona, answering the sign-in request with the nonce, with the claims added or overridden
     */
    public String idToken(final String nonce, final Consumer<JwtClaimsSet.Builder> claims) {
        final var now = Instant.now();
        final var builder = JwtClaimsSet.builder()
                .issuer(issuer())
                .audience(List.of(CLIENT_ID))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("nonce", nonce);
        claims.accept(builder);
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    /**
     * Answers the exchange of the authorization code with the ID token.
     */
    public void acceptCode(final String code, final String idToken) {
        final var body = """
                {"access_token": "provider-access", "token_type": "Bearer", "expires_in": 300,
                 "id_token": "%s"}""".formatted(idToken);
        stubToken("authorization_code", "code=" + code, okJson(body));
    }

    public void stop() {
        server.stop();
    }

    private void stubToken(final String grantType, final String parameter,
                           final ResponseDefinitionBuilder response) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=" + grantType))
                .withRequestBody(containing(parameter))
                .willReturn(response));
    }

    private void stubDiscovery() {
        final var configuration = """
                {"issuer": "%1$s",
                 "authorization_endpoint": "%1$s/authorize",
                 "token_endpoint": "%1$s%2$s",
                 "jwks_uri": "%1$s/jwks",
                 "response_types_supported": ["code"],
                 "subject_types_supported": ["public"],
                 "id_token_signing_alg_values_supported": ["RS256"],
                 "token_endpoint_auth_methods_supported": ["client_secret_basic"]}""".formatted(issuer(), TOKEN_PATH);
        server.stubFor(get("/.well-known/openid-configuration").willReturn(okJson(configuration)));
        final var keys = new JWKSet(signingKey.toPublicJWK());
        server.stubFor(get("/jwks").willReturn(okJson(keys.toString())));
    }
}
