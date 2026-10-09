package org.sona.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.auth.TestUsers;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
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
    void aUserWhoMustChangeTheirPasswordCanSeeThatTheyMust() {
        users.create("root", UserRole.ADMIN, AccountState.PASSWORD_CHANGE_REQUIRED);
        final var session = api.session("root", "root");

        final var me = api.get(ApiRoutes.CURRENT_USER, session.accessToken())
                .expectStatus().isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();

        assertThat(me.state()).isEqualTo(AccountState.PASSWORD_CHANGE_REQUIRED);
        assertThat(me.permissions()).isEmpty();
    }

    @Test
    void aUserWhoMustChangeTheirPasswordIsRefusedEverythingElse() {
        users.create("root", UserRole.ADMIN, AccountState.PASSWORD_CHANGE_REQUIRED);
        final var session = api.session("root", "root");

        api.get("/api/library", session.accessToken())
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("password_change_required");
    }

    @Test
    void changingThePasswordActivatesTheAccount() {
        users.create("root", UserRole.ADMIN, AccountState.PASSWORD_CHANGE_REQUIRED);
        final var oldSession = api.session("root", "root");

        api.changePassword(oldSession.accessToken(), "root", "s3cret").expectStatus().isNoContent();

        final var newSession = api.session("root", "s3cret");

        final var me = api.get(ApiRoutes.CURRENT_USER, newSession.accessToken())
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();
        assertThat(me.state()).isEqualTo(AccountState.ACTIVE);
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

        api.changePassword(session.accessToken(), "wrong", "s3cret").expectStatus().isBadRequest();
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() {
        users.create("alice", UserRole.USER);
        final var session = api.session("alice", "alice");

        api.changePassword(session.accessToken(), "alice", "alice").expectStatus().isBadRequest();
        api.changePassword(session.accessToken(), "alice", " ").expectStatus().isBadRequest();
    }
}
