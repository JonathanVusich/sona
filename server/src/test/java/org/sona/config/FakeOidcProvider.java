package org.sona.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * An OIDC provider served by WireMock, with its own signing key.
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

    public FakeOidcProvider() throws JOSEException {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        signingKey = new RSAKeyGenerator(2048).keyID("fake-oidc").generate();
        stubDiscovery();
    }

    public String issuer() {
        return server.baseUrl();
    }

    public void stop() {
        server.stop();
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
