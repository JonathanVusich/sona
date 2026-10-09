package org.sona.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.FakeOidcProvider;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.db.LocalUserDao;
import org.sona.db.OidcUserDao;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv5;

/**
 * Sona accepts access tokens from a trusted OIDC provider, so users signed in there need no second login.
 */
@IntegrationTest
class OidcTokenTest {

    @Autowired
    FakeOidcProvider provider;

    @Autowired
    LocalUserDao localUserDao;

    @Autowired
    OidcUserDao oidcUserDao;

    @Autowired
    PasswordEncoder encoder;

    @Value("${local.server.port}")
    int port;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void aProviderTokenSignsInANewUser() {
        final var token = provider.token(claims -> claims.subject("alice-id").claim("preferred_username", "alice"));

        final var me = me(token);

        assertThat(me.username()).isEqualTo("alice");
        assertThat(me.role()).isEqualTo(UserRole.USER);
        assertThat(me.permissions()).containsExactly(Permission.LIBRARY_READ);
        assertThat(me.userId()).isEqualTo(uuidv5(provider.issuer() + "\n" + "alice-id"));
        assertThat(oidcUserDao.find(me.userId())).isPresent();
    }

    @Test
    void laterTokensFindTheSameUser() {
        final var token = provider.token(claims -> claims.subject("alice-id").claim("preferred_username", "alice"));

        final var first = me(token);
        final var second = me(token);

        assertThat(second.userId()).isEqualTo(first.userId());
    }

    @Test
    void theAdminGroupMakesTheUserAnAdmin() {
        final var token = provider.token(claims -> claims.subject("root-id")
                .claim("groups", List.of("staff", FakeOidcProvider.ADMIN_GROUP)));

        assertThat(me(token).role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void theRoleFollowsTheProviderGroups() {
        final var asAdmin = provider.token(claims -> claims.subject("root-id")
                .claim("groups", List.of(FakeOidcProvider.ADMIN_GROUP)));
        final var asUser = provider.token(claims -> claims.subject("root-id").claim("groups", List.of("staff")));

        me(asAdmin);

        assertThat(me(asUser).role()).isEqualTo(UserRole.USER);
        final var stored = oidcUserDao.find(uuidv5(provider.issuer() + "\n" + "root-id")).orElseThrow();
        assertThat(stored.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void providerTokensAreTrustedWithoutTheDatabase() {
        final var token = provider.token(claims -> claims.subject("alice-id")
                .claim("groups", List.of(FakeOidcProvider.ADMIN_GROUP)));

        // Past authentication and the admin's permissions, so a missing endpoint isn't refused.
        api.get("/api/library", token).expectStatus().isNotFound();

        assertThat(oidcUserDao.find(uuidv5(provider.issuer() + "\n" + "alice-id"))).isEmpty();
    }

    @Test
    void aTakenUsernameFallsBackToTheSubject() {
        new TestLocalUsers(localUserDao, encoder).create("bob", UserRole.USER);
        final var token = provider.token(claims -> claims.subject("bob-id").claim("preferred_username", "bob"));

        assertThat(me(token).username()).isEqualTo("bob-id@localhost");
    }

    @Test
    void aUsernameTakenByAnotherProviderUserFallsBackToTheSubject() {
        me(provider.token(claims -> claims.subject("bob-id").claim("preferred_username", "bob")));
        final var token = provider.token(claims -> claims.subject("other-bob-id").claim("preferred_username", "bob"));

        assertThat(me(token).username()).isEqualTo("other-bob-id@localhost");
    }

    @Test
    void aProviderUserHasNoPasswordToChange() {
        final var token = provider.token(claims -> claims.subject("alice-id"));

        api.changePassword(token, "old", "new")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(202);
    }

    @Test
    void aTokenForAnotherAudienceIsRejected() {
        final var token = provider.token(claims -> claims.subject("alice-id").audience(List.of("another-app")));

        api.get(ApiRoutes.CURRENT_USER, token).expectStatus().isUnauthorized();
    }

    @Test
    void anExpiredTokenIsRejected() {
        final var token = provider.token(claims -> claims.subject("alice-id")
                .issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().minusSeconds(1800)));

        api.get(ApiRoutes.CURRENT_USER, token).expectStatus().isUnauthorized();
    }

    @Test
    void aTokenFromAnUntrustedIssuerIsRejected() {
        final var token = provider.token(claims -> claims.subject("alice-id").issuer("https://elsewhere.example"));

        api.get(ApiRoutes.CURRENT_USER, token).expectStatus().isUnauthorized();
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws JOSEException {
        final var otherKey = new RSAKeyGenerator(2048).keyID("fake-oidc").generate();
        final var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));
        final var claims = JwtClaimsSet.builder()
                .issuer(provider.issuer())
                .audience(List.of(FakeOidcProvider.AUDIENCE))
                .subject("alice-id")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        final var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        api.get(ApiRoutes.CURRENT_USER, token).expectStatus().isUnauthorized();
    }

    private CurrentUserResponse me(final String token) {
        return api.get(ApiRoutes.CURRENT_USER, token)
                .expectStatus().isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();
    }
}
