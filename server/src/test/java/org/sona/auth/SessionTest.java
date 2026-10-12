package org.sona.auth;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.USERS;
import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Refreshing and ending the sessions Sona issues. Each refresh replaces the refresh token, and using a replaced one
 * ends its session.
 */
@IntegrationTest
class SessionTest {

    @Autowired
    TokenIssuer tokenIssuer;

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    DSLContext dsl;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    Users alice;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        alice = new Users(uuidv7(), UserType.LOCAL, "alice", UserRole.USER, AccountState.ACTIVE);
        userDao.insert(alice);
        passwordDao.upsert(new Password(alice.userId(), "{noop}alice", PasswordState.VALID));
    }

    @Test
    void refreshReturnsAnAccessTokenAndANewRefreshCookie() {
        final var first = tokenIssuer.startSession(alice);

        final var result = api.refreshSession(first.refreshToken())
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult();

        assertThat(result.getResponseBody().tokenType()).isEqualTo(TokenType.BEARER);
        assertThat(result.getResponseBody().expiresIn()).isEqualTo(900);
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("sona_refresh=")
                .doesNotContain(first.refreshToken())
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict");
        api.getWithToken(ApiRoutes.CURRENT_USER, result.getResponseBody().accessToken()).expectStatus().isOk();
    }

    @Test
    void aDisabledUserCannotRefreshTheirSession() {
        final var session = tokenIssuer.startSession(alice);

        dsl.update(USERS)
                .set(USERS.STATE, AccountState.DISABLED)
                .where(USERS.USER_ID.eq(alice.userId()))
                .execute();

        // The access token is trusted until it expires, and the refresh is where the account is checked again.
        api.refreshSession(session.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void reusingAReplacedRefreshTokenEndsTheSession() {
        final var first = tokenIssuer.startSession(alice);
        final var second = ApiClient.readSession(api.refreshSession(first.refreshToken()));

        api.refreshSession(first.refreshToken()).expectStatus().isUnauthorized();

        api.refreshSession(second.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void reuseOnlyEndsTheSessionTheTokenBelongsTo() {
        final var laptop = tokenIssuer.startSession(alice);
        final var phone = tokenIssuer.startSession(alice);
        ApiClient.readSession(api.refreshSession(laptop.refreshToken()));

        api.refreshSession(laptop.refreshToken()).expectStatus().isUnauthorized();

        api.refreshSession(phone.refreshToken()).expectStatus().isOk();
    }

    @Test
    void refreshWithAnUnknownTokenIsRejected() {
        api.refreshSession("unknown")
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(102);
    }

    @Test
    void logoutEndsTheSession() {
        final var session = tokenIssuer.startSession(alice);

        api.logout(session.refreshToken())
                .expectStatus().isNoContent()
                .expectHeader().value(HttpHeaders.SET_COOKIE, cookie -> assertThat(cookie).contains("Max-Age=0"));

        api.refreshSession(session.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void logoutNeedsARefreshToken() {
        api.logoutWithoutCookie()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(102);
    }

    @Test
    void logoutRejectsAnUnknownRefreshToken() {
        api.logout("unknown")
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(102);
    }

    @Test
    void logoutRejectsARefreshTokenThatWasAlreadyUsed() {
        final var session = tokenIssuer.startSession(alice);
        api.logout(session.refreshToken()).expectStatus().isNoContent();

        api.logout(session.refreshToken()).expectStatus().isUnauthorized();
    }
}
