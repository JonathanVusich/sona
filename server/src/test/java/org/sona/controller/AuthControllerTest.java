package org.sona.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.Permission;
import org.sona.auth.TestLocalUsers;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.db.LocalUserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.LOCAL_USER;

@IntegrationTest
class AuthControllerTest {

    @Autowired
    LocalUserDao localUserDao;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    DSLContext dsl;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    TestLocalUsers users;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        users = new TestLocalUsers(localUserDao, encoder);
    }

    @Test
    void loginReturnsAnAccessTokenAndARefreshCookie() {
        users.create("alice", UserRole.USER);

        final var result = api.login("alice", "alice")
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult();

        assertThat(result.getResponseBody().tokenType()).isEqualTo(TokenType.BEARER);
        assertThat(result.getResponseBody().expiresIn()).isEqualTo(900);
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("sona_refresh=")
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    void anAccessTokenIdentifiesTheUserAndTheirPermissions() {
        final var alice = users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        final var me = api.get(ApiRoutes.CURRENT_USER, session.accessToken())
                .expectStatus().isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();

        assertThat(me).isEqualTo(new CurrentUserResponse(alice.userId(), "alice", UserRole.USER,
                AccountState.ACTIVE, List.of(Permission.LIBRARY_READ)));
    }

    @Test
    void adminsHaveEveryPermission() {
        users.create("root", UserRole.ADMIN);
        final var session = api.session("root", "root");

        final var me = api.get(ApiRoutes.CURRENT_USER, session.accessToken())
                .expectStatus().isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();

        assertThat(me.permissions()).containsExactly(Permission.values());
    }

    @Test
    void loginRejectsAWrongPassword() {
        users.create("alice", UserRole.USER);

        api.login("alice", "wrong").expectStatus().isUnauthorized();
    }

    @Test
    void loginRejectsAnUnknownUser() {
        api.login("nobody", "nobody").expectStatus().isUnauthorized();
    }

    @Test
    void loginRejectsADisabledUser() {
        users.create("alice", UserRole.USER, AccountState.DISABLED);

        api.login("alice", "alice").expectStatus().isUnauthorized();
    }

    @Test
    void requestsWithoutATokenAreRejected() {
        api.getAnonymously(ApiRoutes.CURRENT_USER).expectStatus().isUnauthorized();
    }

    @Test
    void requestsWithAnInvalidTokenAreRejected() {
        api.get(ApiRoutes.CURRENT_USER, "not-a-jwt").expectStatus().isUnauthorized();
    }

    @Test
    void aDisabledUserCannotRefreshTheirSession() {
        final var alice = users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        disable(alice.username());

        // The access token is trusted until it expires, and the refresh is where the account is checked again.
        api.refresh(session.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void refreshReplacesTheRefreshToken() {
        users.create("alice", UserRole.USER);
        final var first = api.session("alice", "alice");

        final var second = ApiClient.session(api.refresh(first.refreshToken()));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        api.get(ApiRoutes.CURRENT_USER, second.accessToken()).expectStatus().isOk();
    }

    @Test
    void reusingAReplacedRefreshTokenEndsTheSession() {
        users.create("alice", UserRole.USER);
        final var first = api.session("alice", "alice");
        final var second = ApiClient.session(api.refresh(first.refreshToken()));

        api.refresh(first.refreshToken()).expectStatus().isUnauthorized();

        api.refresh(second.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void reuseOnlyEndsTheSessionTheTokenBelongsTo() {
        users.create("alice", UserRole.USER);
        final var laptop = api.session("alice", "alice");
        final var phone = api.session("alice", "alice");
        ApiClient.session(api.refresh(laptop.refreshToken()));

        api.refresh(laptop.refreshToken()).expectStatus().isUnauthorized();

        api.refresh(phone.refreshToken()).expectStatus().isOk();
    }

    @Test
    void refreshWithAnUnknownTokenIsRejected() {
        api.refresh("unknown").expectStatus().isUnauthorized();
    }

    @Test
    void logoutEndsTheSession() {
        users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        api.logout(session.refreshToken())
                .expectStatus().isNoContent()
                .expectHeader().value(HttpHeaders.SET_COOKIE, cookie -> assertThat(cookie).contains("Max-Age=0"));

        api.refresh(session.refreshToken()).expectStatus().isUnauthorized();
    }

    private void disable(final String username) {
        // There's no endpoint or DAO method for disabling users yet.
        dsl.update(LOCAL_USER)
                .set(LOCAL_USER.STATE, AccountState.DISABLED)
                .where(LOCAL_USER.USERNAME.eq(username))
                .execute();
    }
}
