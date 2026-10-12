package org.sona.auth;

import org.junit.jupiter.api.Test;
import org.sona.model.tables.pojos.OidcSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OidcUtilsTest {

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("idp")
            .clientId("sona")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://sona.example/callback")
            .authorizationUri("https://idp.example/authorize")
            .tokenUri("https://idp.example/token")
            .scope("openid", "offline_access")
            .build();

    private static final OAuth2AuthorizationRequest AUTHORIZATION = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://idp.example/authorize")
            .clientId("sona")
            .build();

    @Test
    void refreshGrantRequestCarriesTheProviderRefreshTokenAndScopes() {
        final var session = new OidcSession(UUID.randomUUID(), UUID.randomUUID(), "provider-refresh");

        final var grant = OidcUtils.refreshGrantRequest(REGISTRATION, session);

        assertThat(grant.getClientRegistration()).isSameAs(REGISTRATION);
        assertThat(grant.getRefreshToken().getTokenValue()).isEqualTo("provider-refresh");
        assertThat(grant.getAccessToken().getScopes()).isEqualTo(Set.of("openid", "offline_access"));
    }

    @Test
    void silentIfAskedAddsPromptNoneWhenAsked() {
        final var request = new MockHttpServletRequest();
        request.setParameter("prompt", "none");

        final var authorization = OidcUtils.silentIfAsked(request, AUTHORIZATION);

        assertThat(authorization.getAdditionalParameters()).containsEntry("prompt", "none");
    }

    @Test
    void silentIfAskedLeavesOtherPromptsOut() {
        final var request = new MockHttpServletRequest();
        request.setParameter("prompt", "login");

        final var authorization = OidcUtils.silentIfAsked(request, AUTHORIZATION);

        assertThat(authorization.getAdditionalParameters()).doesNotContainKey("prompt");
    }

    @Test
    void silentIfAskedPassesNoAuthorizationThrough() {
        final var request = new MockHttpServletRequest();
        request.setParameter("prompt", "none");

        assertThat(OidcUtils.silentIfAsked(request, null)).isNull();
    }
}
