package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.SessionState;
import org.sona.auth.TestUsers;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class UserControllerTest {

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
    void changingThePasswordActivatesTheAccount() {
        users.create("root", UserRole.ADMIN, PasswordState.CHANGE_REQUIRED);
        final var oldSession = api.session("root", "root");

        api.changePassword(oldSession.accessToken(), "root", "s3cret").expectStatus().isNoContent();

        final var newSession = api.session("root", "s3cret");

        final var me = api.get(ApiRoutes.CURRENT_USER, newSession.accessToken())
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();
        assertThat(me.state()).isEqualTo(SessionState.ACTIVE);
        assertThat(me.permissions()).hasSize(4);
        // Past the password check, so a missing endpoint is no longer refused.
        api.get("/api/library", newSession.accessToken()).expectStatus().isNotFound();
    }

    @Test
    void changingThePasswordReplacesTheOldOne() {
        users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "s3cret").expectStatus().isNoContent();

        api.login("alice", "alice").expectStatus().isUnauthorized();
        api.login("alice", "s3cret").expectStatus().isOk();
    }

    @Test
    void changingThePasswordEndsEverySession() {
        users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");
        final var otherSession = api.session("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "s3cret").expectStatus().isNoContent();

        api.refresh(session.refreshToken()).expectStatus().isUnauthorized();
        api.refresh(otherSession.refreshToken()).expectStatus().isUnauthorized();
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() {
        users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        api.changePassword(session.accessToken(), "wrong", "s3cret")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(200);
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() {
        users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "alice")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(201);
        api.changePassword(session.accessToken(), "alice", " ").expectStatus().isBadRequest();
    }
}
