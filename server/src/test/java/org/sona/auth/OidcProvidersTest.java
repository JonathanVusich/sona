package org.sona.auth;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.FakeOidcProvider;
import org.sona.controller.ApiClient;
import org.sona.controller.ApiRoutes;
import org.sona.controller.response.OidcProviderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OIDC providers users can sign in through, and Sona's registrations there.
 */
@IntegrationTest
class OidcProvidersTest {

    @Autowired
    OidcDiscoveryClient discoveryClient;

    @Autowired
    FakeOidcProvider provider;

    @Value("${local.server.port}")
    int port;

    @Test
    void theProvidersAreListed() {
        final var providers = new ApiClient(port).getAnonymously(ApiRoutes.OIDC_PROVIDERS)
                .expectStatus().isOk()
                .expectBody(OidcProviderResponse[].class)
                .returnResult().getResponseBody();

        assertThat(providers).containsExactly(new OidcProviderResponse(FakeOidcProvider.ID, FakeOidcProvider.NAME));
    }

    @Test
    void aRegistrationIsDiscoveredFromTheProvider() {
        final var registration = discoveryClient.findByRegistrationId(FakeOidcProvider.ID);

        assertThat(registration.getClientId()).isEqualTo(FakeOidcProvider.CLIENT_ID);
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(provider.issuer() + "/token");
        assertThat(registration.getRedirectUri()).endsWith(ApiRoutes.OIDC_CALLBACK + "/{registrationId}");
        assertThat(registration.getClientSettings().isRequireProofKey()).isTrue();
    }

    @Test
    void anUnknownProviderHasNoRegistration() {
        assertThat(discoveryClient.findByRegistrationId("unknown")).isNull();
        assertThat(discoveryClient.findByIssuer("https://elsewhere.example")).isEmpty();
    }
}
