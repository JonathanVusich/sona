package org.sona.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.model.Tables.OIDC_SESSION;
import static org.sona.model.Tables.USERS;
import static org.sona.utils.IDGenerator.uuidv5;
import static org.sona.utils.IDGenerator.uuidv7;

import java.net.HttpCookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.FakeOidcProvider;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.controller.response.CurrentUserResponse;
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
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Signing in through an OIDC provider with the authorization code flow, registering on the first sign-in, and
 * checking the session with the provider on every refresh.
 */
@IntegrationTest
class OidcSignInTest {

    @Autowired
    FakeOidcProvider provider;

    @Autowired
    UserDao userDao;

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
    void signingInUsesPkce() {
        final var parameters = authorizeParameters(api.startOidcSignIn(FakeOidcProvider.ID));

        assertThat(parameters.getFirst("code_challenge")).isNotBlank();
        assertThat(parameters.getFirst("code_challenge_method")).isEqualTo("S256");
    }

    @Test
    void aSilentSignInAsksTheProviderToShowNothing() {
        final var silent = authorizeParameters(api.startSilentOidcSignIn(FakeOidcProvider.ID));
        final var normal = authorizeParameters(api.startOidcSignIn(FakeOidcProvider.ID));

        assertThat(silent.getFirst("prompt")).isEqualTo("none");
        assertThat(normal.getFirst("prompt")).isNull();
    }

    @Test
    void aSilentSignInWithoutAProviderSessionLeavesTheUserToSignIn() {
        final var start = startSignIn(api.startSilentOidcSignIn(FakeOidcProvider.ID));

        final var signIn = api.failOidcSignIn(
                        FakeOidcProvider.ID, "login_required", start.state(), start.sessionCookie())
                .expectStatus()
                .isFound()
                .expectBody()
                .isEmpty();

        assertThat(redirectLocation(signIn)).isEqualTo("/#error=108");
    }

    @Test
    void aSignInRemembersTheProvider() {
        existingUser("alice-id", "alice");

        final var signIn = signIn("alice-id", List.of());

        assertThat(findCookie(signIn, SignInHandler.LAST_PROVIDER_COOKIE)).contains(FakeOidcProvider.ID);
    }

    @Test
    void aNewUserIsSentToRegister() {
        final var signIn = signIn("alice-id", List.of());

        assertThat(redirectLocation(signIn)).startsWith("/register#registration_token=");
        assertThat(refreshCookie(signIn)).isEmpty();
        assertThat(userDao.loadById(userId("alice-id"))).isEmpty();
    }

    @Test
    void aRegisteredUserIsSignedIn() {
        existingUser("alice-id", "alice");

        final var session = providerSession("alice-id", List.of());

        final var me = currentUser(session.accessToken());
        assertThat(me.userId()).isEqualTo(userId("alice-id"));
        assertThat(me.username()).isEqualTo("alice");
        assertThat(me.role()).isEqualTo(UserRole.USER);
        assertThat(me.permissions()).containsExactly(Permission.LIBRARY_READ);
    }

    @Test
    void theRoleFollowsTheProviderGroupsAtEachSignIn() {
        existingUser("root-id", "root");

        final var asAdmin = providerSession("root-id", List.of(FakeOidcProvider.ADMIN_GROUP));
        assertThat(currentUser(asAdmin.accessToken()).role()).isEqualTo(UserRole.ADMIN);

        final var asUser = providerSession("root-id", List.of("staff"));
        assertThat(currentUser(asUser.accessToken()).role()).isEqualTo(UserRole.USER);
        assertThat(userDao.loadById(userId("root-id")).orElseThrow().role()).isEqualTo(UserRole.USER);
    }

    @Test
    void aDisabledUserIsTurnedAway() {
        existingUser("alice-id", "alice");
        dsl.update(USERS)
                .set(USERS.STATE, AccountState.DISABLED)
                .where(USERS.USER_ID.eq(userId("alice-id")))
                .execute();

        assertThat(redirectLocation(signIn("alice-id", List.of()))).isEqualTo("/#error=105");
    }

    @Test
    void aSignInSonaDidNotStartFails() {
        final var start = startSignIn();

        final var signIn = api.finishOidcSignIn(FakeOidcProvider.ID, "code", "forged-state", start.sessionCookie())
                .expectStatus()
                .isFound()
                .expectBody()
                .isEmpty();

        assertThat(redirectLocation(signIn)).isEqualTo("/#error=106");
    }

    @Test
    void registeringCreatesTheAccount() {
        final var registered = api.registerUser(registrationToken("alice-id"), "alice")
                .expectStatus()
                .isCreated()
                .expectBody()
                .isEmpty();

        assertThat(registered.getResponseHeaders().getLocation())
                .hasToString(ApiRoutes.OIDC_AUTHORIZE + "/" + FakeOidcProvider.ID);
        final var me = currentUser(providerSession("alice-id", List.of()).accessToken());
        assertThat(me.username()).isEqualTo("alice");
        assertThat(userDao.loadById(userId("alice-id")).orElseThrow().type()).isEqualTo(UserType.OIDC);
        assertThat(userDao.loadOidcIdentity(userId("alice-id")))
                .contains(new OidcIdentity(userId("alice-id"), provider.issuer(), "alice-id"));
    }

    @Test
    void aTakenUsernameIsRefused() {
        existingUser("other-id", "bob");

        api.registerUser(registrationToken("bob-id"), "bob")
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(203);
    }

    @Test
    void aBlankUsernameIsRefused() {
        api.registerUser(registrationToken("alice-id"), " ")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    void registeringTwiceIsRefused() {
        final var token = registrationToken("alice-id");
        api.registerUser(token, "alice").expectStatus().isCreated();

        api.registerUser(token, "alice2")
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(205);
    }

    @Test
    void anInvalidRegistrationTokenIsRefused() {
        api.registerUser("not-a-token", "alice")
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(107);
    }

    @Test
    void aRegistrationTokenIsNotAnAccessToken() {
        api.getWithToken(ApiRoutes.CURRENT_USER, registrationToken("alice-id"))
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void aProviderUserHasNoPasswordToChange() {
        existingUser("alice-id", "alice");
        final var session = providerSession("alice-id", List.of());

        api.changePassword(session.accessToken(), "old", "new")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(202);
    }

    @Test
    void aProviderUserCannotLogInWithAPassword() {
        existingUser("alice-id", "alice");

        api.login("alice", "s3cret").expectStatus().isUnauthorized();
    }

    @Test
    void aProviderThatGivesNoRefreshTokenFailsTheSignIn() {
        existingUser("alice-id", "alice");
        final var start = startSignIn();
        final var code = "code-" + uuidv7();
        final var idToken = provider.idToken(start.nonce(), claims -> claims.subject("alice-id"));
        provider.acceptCodeWithoutRefreshToken(code, idToken);

        final var signIn = api.finishOidcSignIn(FakeOidcProvider.ID, code, start.state(), start.sessionCookie())
                .expectStatus()
                .isFound()
                .expectBody()
                .isEmpty();

        assertThat(redirectLocation(signIn)).isEqualTo("/#error=106");
    }

    @Test
    void eachRefreshIsCheckedWithTheProvider() {
        existingUser("alice-id", "alice");
        final var cookie = signedInCookie("alice-id", List.of(), "alice-refresh-1");
        provider.acceptRefresh("alice-refresh-1", "alice-refresh-2");
        provider.acceptRefresh("alice-refresh-2", "alice-refresh-3");

        final var first = ApiClient.readSession(api.refreshSession(cookie));
        ApiClient.readSession(api.refreshSession(first.refreshToken()));

        // The provider rotated its refresh token, and Sona used the new one.
        assertThat(provider.refreshCount("alice-refresh-1")).isEqualTo(1);
        assertThat(provider.refreshCount("alice-refresh-2")).isEqualTo(1);
    }

    @Test
    void aSessionTheProviderRefusesEnds() {
        existingUser("alice-id", "alice");
        final var cookie = signedInCookie("alice-id", List.of(), "alice-refresh");
        provider.rejectRefresh("alice-refresh");

        api.refreshSession(cookie)
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(102);
    }

    @Test
    void loggingOutForgetsTheProviderRefreshToken() {
        existingUser("alice-id", "alice");
        final var cookie = signedInCookie("alice-id", List.of(), "alice-refresh");
        assertThat(dsl.fetchCount(OIDC_SESSION)).isEqualTo(1);

        api.logout(cookie).expectStatus().isNoContent();

        assertThat(dsl.fetchCount(OIDC_SESSION)).isZero();
    }

    /**
     * The sign-in Sona started at the provider, as the browser carries it there and back.
     *
     * @param sessionCookie the cookie Sona keeps the sign-in's state in
     */
    private record StartedSignIn(String state, String nonce, HttpCookie sessionCookie) {}

    private void existingUser(final String subject, final String username) {
        final var userId = userId(subject);
        userDao.insert(new Users(userId, UserType.OIDC, username, UserRole.USER, AccountState.ACTIVE));
        userDao.insertOidcIdentity(new OidcIdentity(userId, provider.issuer(), subject));
    }

    /**
     * @return the registration token a sign-in by a new user gives the frontend
     */
    private String registrationToken(final String subject) {
        final var signIn = signIn(subject, List.of());
        final var fragment = UriComponentsBuilder.fromUriString(redirectLocation(signIn))
                .build()
                .getFragment();
        return fragment.substring("registration_token=".length());
    }

    private StartedSignIn startSignIn() {
        return startSignIn(api.startOidcSignIn(FakeOidcProvider.ID));
    }

    private StartedSignIn startSignIn(final RestTestClient.ResponseSpec response) {
        final var start = response.expectStatus().isFound().expectBody().isEmpty();
        final var parameters = authorizeParameters(start);
        final var setCookie = start.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return new StartedSignIn(
                urlDecode(parameters.getFirst("state")),
                urlDecode(parameters.getFirst("nonce")),
                HttpCookie.parse(setCookie).getFirst());
    }

    /**
     * @return the query parameters Sona sends the browser to the provider with
     */
    private static MultiValueMap<String, String> authorizeParameters(final RestTestClient.ResponseSpec response) {
        return authorizeParameters(
                response.expectStatus().isFound().expectBody().isEmpty());
    }

    private static MultiValueMap<String, String> authorizeParameters(final EntityExchangeResult<Void> start) {
        return UriComponentsBuilder.fromUri(start.getResponseHeaders().getLocation())
                .build()
                .getQueryParams();
    }

    /**
     * Signs in at the provider and comes back to Sona.
     *
     * @return Sona's redirect back to the frontend
     */
    private EntityExchangeResult<Void> signIn(final String subject, final List<String> groups) {
        return signIn(subject, groups, "refresh-" + uuidv7());
    }

    /**
     * @param providerRefreshToken the refresh token the provider gives Sona
     */
    private EntityExchangeResult<Void> signIn(
            final String subject, final List<String> groups, final String providerRefreshToken) {
        final var start = startSignIn();
        final var code = "code-" + uuidv7();
        final var idToken = provider.idToken(
                start.nonce(), claims -> claims.subject(subject).claim("groups", groups));
        provider.acceptCode(code, idToken, providerRefreshToken);
        return api.finishOidcSignIn(FakeOidcProvider.ID, code, start.state(), start.sessionCookie())
                .expectStatus()
                .isFound()
                .expectBody()
                .isEmpty();
    }

    /**
     * @return the session the frontend gets by refreshing after a sign-in, which the provider accepts
     */
    private ApiClient.Session providerSession(final String subject, final List<String> groups) {
        final var providerRefreshToken = "refresh-" + uuidv7();
        final var cookie = signedInCookie(subject, groups, providerRefreshToken);
        provider.acceptRefresh(providerRefreshToken, providerRefreshToken + "-next");
        return ApiClient.readSession(api.refreshSession(cookie));
    }

    /**
     * @return the refresh cookie of a sign-in that must start a session
     */
    private String signedInCookie(final String subject, final List<String> groups, final String providerRefreshToken) {
        final var signIn = signIn(subject, groups, providerRefreshToken);
        assertThat(redirectLocation(signIn)).isEqualTo("/");
        return refreshCookie(signIn).orElseThrow();
    }

    private static Optional<String> refreshCookie(final EntityExchangeResult<Void> result) {
        return findCookie(result, RefreshCookieService.NAME);
    }

    private static Optional<String> findCookie(final EntityExchangeResult<Void> result, final String name) {
        final var setCookies =
                Objects.requireNonNullElse(result.getResponseHeaders().get(HttpHeaders.SET_COOKIE), List.<String>of());
        return setCookies.stream()
                .map(setCookie -> HttpCookie.parse(setCookie).getFirst())
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private CurrentUserResponse currentUser(final String accessToken) {
        return api.getWithToken(ApiRoutes.CURRENT_USER, accessToken)
                .expectStatus()
                .isOk()
                .expectBody(CurrentUserResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private UUID userId(final String subject) {
        return uuidv5(provider.issuer() + "\n" + subject);
    }

    /**
     * @return where the response sends the browser, without Sona's origin, which the server adds
     */
    private String redirectLocation(final EntityExchangeResult<Void> result) {
        final var origin = "http://localhost:" + port;
        return result.getResponseHeaders().getLocation().toString().replaceFirst("^" + origin, "");
    }

    private static String urlDecode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
