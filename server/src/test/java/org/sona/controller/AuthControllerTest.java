package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.Permission;
import org.sona.auth.SessionState;
import org.sona.auth.TestUsers;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.controller.response.TokenResponse;
import org.sona.controller.response.TokenType;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class AuthControllerTest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    PasswordEncoder encoder;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    TestUsers users;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        users = new TestUsers(userDao, passwordDao, encoder);
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
                SessionState.ACTIVE, List.of(Permission.LIBRARY_READ)));
    }

    @Test
    void loginRejectsAWrongPassword() {
        users.create("alice", UserRole.USER);

        api.login("alice", "wrong")
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(101)
                .jsonPath("$.reason").isEqualTo("The username or password is incorrect.");
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
}
