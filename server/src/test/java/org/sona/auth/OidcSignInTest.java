package org.sona.auth;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.FakeOidcProvider;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.controller.response.CurrentUserResponse;
import org.sona.db.OidcIdentityDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.HttpCookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.USERS;
import static org.sona.utils.IDGenerator.uuidv5;
import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Signing in through an OIDC provider with the authorization code flow, and registering on the first sign-in.
 */
@IntegrationTest
class OidcSignInTest {

    @Autowired
    FakeOidcProvider provider;

    @Autowired
    UserDao userDao;

    @Autowired
    OidcIdentityDao oidcIdentityDao;

    @Autowired
    DSLContext dsl;

    @Value("${local.server.port}")
    int port;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void aNewUserIsSentToRegister() {
        final var signIn = signIn("alice-id", List.of());

        assertThat(location(signIn)).startsWith("/register#registration_token=");
        assertThat(refreshCookie(signIn)).isEmpty();
        assertThat(userDao.find(userId("alice-id"))).isEmpty();
    }

    @Test
    void aRegisteredUserIsSignedIn() {
        existingUser("alice-id", "alice");

        final var session = session("alice-id", List.of());

        final var me = me(session.accessToken());
        assertThat(me.userId()).isEqualTo(userId("alice-id"));
        assertThat(me.username()).isEqualTo("alice");
        assertThat(me.role()).isEqualTo(UserRole.USER);
        assertThat(me.permissions()).containsExactly(Permission.LIBRARY_READ);
    }

    @Test
    void theRoleFollowsTheProviderGroupsAtEachSignIn() {
        existingUser("root-id", "root");

        final var asAdmin = session("root-id", List.of(FakeOidcProvider.ADMIN_GROUP));
        assertThat(me(asAdmin.accessToken()).role()).isEqualTo(UserRole.ADMIN);

        final var asUser = session("root-id", List.of("staff"));
        assertThat(me(asUser.accessToken()).role()).isEqualTo(UserRole.USER);
        assertThat(userDao.find(userId("root-id")).orElseThrow().role()).isEqualTo(UserRole.USER);
    }

    @Test
    void aDisabledUserIsTurnedAway() {
        existingUser("alice-id", "alice");
        dsl.update(USERS)
                .set(USERS.STATE, AccountState.DISABLED)
                .where(USERS.USER_ID.eq(userId("alice-id")))
                .execute();

        assertThat(location(signIn("alice-id", List.of()))).isEqualTo("/#error=105");
    }

    @Test
    void aSignInSonaDidNotStartFails() {
        final var start = startSignIn();

        final var signIn = api.finishOidcSignIn(FakeOidcProvider.ID, "code", "forged-state", start.sessionCookie())
                .expectStatus().isFound()
                .expectBody().isEmpty();

        assertThat(location(signIn)).isEqualTo("/#error=106");
    }

    @Test
    void registeringCreatesTheAccount() {
        final var registered = api.register(registrationToken("alice-id"), "alice")
                .expectStatus().isCreated()
                .expectBody().isEmpty();

        assertThat(registered.getResponseHeaders().getLocation())
                .hasToString(ApiRoutes.OIDC_AUTHORIZE + "/" + FakeOidcProvider.ID);
        final var me = me(session("alice-id", List.of()).accessToken());
        assertThat(me.username()).isEqualTo("alice");
        assertThat(userDao.find(userId("alice-id")).orElseThrow().type()).isEqualTo(UserType.OIDC);
        assertThat(oidcIdentityDao.find(userId("alice-id")))
                .contains(new OidcIdentity(userId("alice-id"), provider.issuer(), "alice-id"));
    }

    @Test
    void aTakenUsernameIsRefused() {
        existingUser("other-id", "bob");

        api.register(registrationToken("bob-id"), "bob")
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo(203);
    }

    @Test
    void aBlankUsernameIsRefused() {
        api.register(registrationToken("alice-id"), " ")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(204);
    }

    @Test
    void registeringTwiceIsRefused() {
        final var token = registrationToken("alice-id");
        api.register(token, "alice").expectStatus().isCreated();

        api.register(token, "alice2")
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo(205);
    }

    @Test
    void anInvalidRegistrationTokenIsRefused() {
        api.register("not-a-token", "alice")
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(107);
    }

    @Test
    void aRegistrationTokenIsNotAnAccessToken() {
        api.get(ApiRoutes.CURRENT_USER, registrationToken("alice-id")).expectStatus().isUnauthorized();
    }

    @Test
    void aProviderUserHasNoPasswordToChange() {
        existingUser("alice-id", "alice");
        final var session = session("alice-id", List.of());

        api.changePassword(session.accessToken(), "old", "new")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(202);
    }

    @Test
    void aProviderUserCannotLogInWithAPassword() {
        existingUser("alice-id", "alice");

        api.login("alice", "").expectStatus().isUnauthorized();
    }

    /**
     * The sign-in Sona started at the provider, as the browser carries it there and back.
     *
     * @param sessionCookie the cookie Sona keeps the sign-in's state in
     */
    private record StartedSignIn(String state, String nonce, HttpCookie sessionCookie) {
    }

    private void existingUser(final String subject, final String username) {
        final var userId = userId(subject);
        userDao.insert(new Users(userId, UserType.OIDC, username, UserRole.USER, AccountState.ACTIVE));
        oidcIdentityDao.insert(new OidcIdentity(userId, provider.issuer(), subject));
    }

    /**
     * @return the registration token a sign-in by a new user gives the frontend
     */
    private String registrationToken(final String subject) {
        final var signIn = signIn(subject, List.of());
        final var fragment = UriComponentsBuilder.fromUriString(location(signIn)).build().getFragment();
        return fragment.substring("registration_token=".length());
    }

    private StartedSignIn startSignIn() {
        final var start = api.startOidcSignIn(FakeOidcProvider.ID)
                .expectStatus().isFound()
                .expectBody().isEmpty();
        final var parameters = authorizeParameters(start);
        final var setCookie = start.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return new StartedSignIn(decode(parameters.getFirst("state")), decode(parameters.getFirst("nonce")),
                HttpCookie.parse(setCookie).getFirst());
    }

    /**
     * @return the query parameters Sona sends the browser to the provider with
     */
    private static MultiValueMap<String, String> authorizeParameters(final EntityExchangeResult<Void> start) {
        return UriComponentsBuilder.fromUri(start.getResponseHeaders().getLocation()).build().getQueryParams();
    }

    /**
     * Signs in at the provider and comes back to Sona.
     *
     * @return Sona's redirect back to the frontend
     */
    private EntityExchangeResult<Void> signIn(final String subject, final List<String> groups) {
        final var start = startSignIn();
        final var code = "code-" + uuidv7();
        final var idToken = provider.idToken(start.nonce(), claims -> claims.subject(subject).claim("groups", groups));
        provider.acceptCode(code, idToken);
        return api.finishOidcSignIn(FakeOidcProvider.ID, code, start.state(), start.sessionCookie())
                .expectStatus().isFound()
                .expectBody().isEmpty();
    }

    /**
     * @return the session the frontend gets by refreshing after a sign-in that must start one
     */
    private ApiClient.Session session(final String subject, final List<String> groups) {
        final var signIn = signIn(subject, groups);
        assertThat(location(signIn)).isEqualTo("/");
        return ApiClient.session(api.refresh(refreshCookie(signIn).orElseThrow()));
    }

    private static Optional<String> refreshCookie(final EntityExchangeResult<Void> result) {
        final var setCookies = Objects.requireNonNullElse(result.getResponseHeaders().get(HttpHeaders.SET_COOKIE),
                List.<String>of());
        return setCookies.stream()
                .map(setCookie -> HttpCookie.parse(setCookie).getFirst())
                .filter(cookie -> cookie.getName().equals(RefreshCookies.NAME))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private CurrentUserResponse me(final String accessToken) {
        return api.get(ApiRoutes.CURRENT_USER, accessToken)
                .expectStatus().isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult().getResponseBody();
    }

    private UUID userId(final String subject) {
        return uuidv5(provider.issuer() + "\n" + subject);
    }

    /**
     * @return where the response sends the browser, without Sona's origin, which the server adds
     */
    private String location(final EntityExchangeResult<Void> result) {
        final var origin = "http://localhost:" + port;
        return result.getResponseHeaders().getLocation().toString().replaceFirst("^" + origin, "");
    }

    private static String decode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
