package org.sona.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestUsers;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.db.UserDao;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class UserControllerTest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordEncoder encoder;

    @Value("${local.server.port}")
    int port;

    ApiClient api;
    TestUsers users;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        users = new TestUsers(userDao, encoder);
    }

    @Test
    void changingThePasswordActivatesTheAccount() {
        users.createUser("root", UserRole.ADMIN, PasswordState.CHANGE_REQUIRED);
        final var oldSession = api.startSession("root", "root");

        api.changePassword(oldSession.accessToken(), "root", "s3cret")
                .expectStatus()
                .isNoContent();

        final var newSession = api.startSession("root", "s3cret");

        final var me = api.getWithToken(ApiRoutes.CURRENT_USER, newSession.accessToken())
                .expectBody(CurrentUserResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(me.state()).isEqualTo(SessionState.ACTIVE);
        assertThat(me.permissions()).hasSize(4);
        // Past the password check, so a missing endpoint is no longer refused.
        api.getWithToken("/api/library", newSession.accessToken())
                .expectStatus()
                .isNotFound();
    }

    @Test
    void changingThePasswordReplacesTheOldOne() {
        users.createUser("alice", UserRole.USER);
        final var session = api.startSession("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "s3cret")
                .expectStatus()
                .isNoContent();

        api.login("alice", "alice").expectStatus().isUnauthorized();
        api.login("alice", "s3cret").expectStatus().isOk();
    }

    @Test
    void changingThePasswordEndsEverySession() {
        users.createUser("alice", UserRole.USER);
        final var session = api.startSession("alice", "alice");
        final var otherSession = api.startSession("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "s3cret")
                .expectStatus()
                .isNoContent();

        api.refreshSession(session.refreshToken()).expectStatus().isUnauthorized();
        api.refreshSession(otherSession.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() {
        users.createUser("alice", UserRole.USER);
        final var session = api.startSession("alice", "alice");

        api.changePassword(session.accessToken(), "wrong", "s3cret")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(200);
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() {
        users.createUser("alice", UserRole.USER);
        final var session = api.startSession("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "alice")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(201);
    }

    @Test
    void aBlankNewPasswordIsRefused() {
        users.createUser("alice", UserRole.USER);
        final var session = api.startSession("alice", "alice");

        api.changePassword(session.accessToken(), "alice", " ")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }
}
