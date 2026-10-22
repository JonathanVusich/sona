package org.sona.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * What an access token lets its user do. Tokens are trusted as they are, so none of this needs the user's row.
 */
@IntegrationTest
class ApiSecurityTest {

    // The 10MB the tests' library accepts.
    private static final long MAX_UPLOAD_SIZE = 10 * 1024 * 1024;

    @Autowired
    JwtEncoder encoder;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    TestTokens tokens;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        tokens = new TestTokens(encoder);
    }

    @Test
    void anAccessTokenIdentifiesTheUserAndTheirPermissions() {
        final var userId = uuidv7();
        final var token = tokens.accessToken(userId, "alice", UserRole.USER, SessionState.ACTIVE);

        assertThat(currentUser(token))
                .isEqualTo(new CurrentUserResponse(
                        userId,
                        "alice",
                        UserRole.USER,
                        SessionState.ACTIVE,
                        List.of(Permission.LIBRARY_READ),
                        MAX_UPLOAD_SIZE));
    }

    @Test
    void adminsHaveEveryPermission() {
        final var token = tokens.accessToken(uuidv7(), "root", UserRole.ADMIN, SessionState.ACTIVE);

        assertThat(currentUser(token).permissions()).containsExactly(Permission.values());
    }

    @Test
    void aUserWhoMustChangeTheirPasswordCanSeeThatTheyMust() {
        final var token = tokens.accessToken(uuidv7(), "root", UserRole.ADMIN, SessionState.PASSWORD_CHANGE_REQUIRED);

        final var me = currentUser(token);

        assertThat(me.state()).isEqualTo(SessionState.PASSWORD_CHANGE_REQUIRED);
        assertThat(me.permissions()).isEmpty();
    }

    @Test
    void aUserWhoMustChangeTheirPasswordIsRefusedEverythingElse() {
        final var token = tokens.accessToken(uuidv7(), "root", UserRole.ADMIN, SessionState.PASSWORD_CHANGE_REQUIRED);

        api.getWithToken("/api/library", token)
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(103)
                .jsonPath("$.reason")
                .isEqualTo("Change your password before continuing.");
    }

    @Test
    void anActiveUserGetsPastTheChecks() {
        final var token = tokens.accessToken(uuidv7(), "root", UserRole.ADMIN, SessionState.ACTIVE);

        // Past authentication and the password check, so a missing endpoint isn't refused.
        api.getWithToken("/api/library", token).expectStatus().isNotFound();
    }

    @Test
    void requestsWithoutATokenAreRejected() {
        api.getAnonymously(ApiRoutes.CURRENT_USER)
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .exists(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(100);
    }

    @Test
    void requestsWithAnInvalidTokenAreRejected() {
        api.getWithToken(ApiRoutes.CURRENT_USER, "not-a-jwt").expectStatus().isUnauthorized();
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws JOSEException {
        final var otherKey = new RSAKeyGenerator(2048).keyID("other").generate();
        final var otherTokens = new TestTokens(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey))));
        final var token = otherTokens.accessToken(uuidv7(), "alice", UserRole.USER, SessionState.ACTIVE);

        api.getWithToken(ApiRoutes.CURRENT_USER, token).expectStatus().isUnauthorized();
    }

    private CurrentUserResponse currentUser(final String token) {
        return api.getWithToken(ApiRoutes.CURRENT_USER, token)
                .expectStatus()
                .isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
