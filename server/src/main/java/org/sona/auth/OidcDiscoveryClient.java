package org.sona.auth;

import org.sona.config.properties.AuthProperties;
import org.sona.config.properties.OidcProvider;
import org.sona.controller.ApiRoutes;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Sona's client registrations at the configured OIDC providers. Each is discovered from its issuer on first use, so
 * Sona still starts while a provider is down.
 */
@Component
public final class OidcDiscoveryClient implements ClientRegistrationRepository {

    private final List<OidcProvider> providers;
    private final Map<String, OidcProvider> providersById;
    private final Map<String, ClientRegistration> discovered = new ConcurrentHashMap<>();

    public OidcDiscoveryClient(final AuthProperties properties) {
        this.providers = properties.oidcProviders();
        this.providersById = providers.stream().collect(toUnmodifiableMap(OidcProvider::id, Function.identity()));
    }

    public List<OidcProvider> listProviders() {
        return providers;
    }

    public Optional<OidcProvider> findProvider(final String registrationId) {
        return Optional.ofNullable(providersById.get(registrationId));
    }

    /**
     * @return the registration of the provider with the issuer, or empty if no configured provider has it
     */
    public Optional<ClientRegistration> findByIssuer(final String issuer) {
        return providers.stream()
                .filter(provider -> provider.issuerUri().equals(issuer))
                .findFirst()
                .map(provider -> findByRegistrationId(provider.id()));
    }

    @Override
    public ClientRegistration findByRegistrationId(final String registrationId) {
        final var provider = providersById.get(registrationId);
        if (provider == null) {
            return null;
        }
        return discovered.computeIfAbsent(registrationId, _ -> discoverRegistration(provider));
    }

    private ClientRegistration discoverRegistration(final OidcProvider provider) {
        // PKCE is off by default for confidential clients like Sona, and recommended for every client.
        final var settings = ClientRegistration.ClientSettings.builder().requireProofKey(true).build();
        return ClientRegistrations.fromIssuerLocation(provider.issuerUri())
                .registrationId(provider.id())
                .clientName(provider.name())
                .clientId(provider.clientId())
                .clientSecret(provider.clientSecret())
                .scope(provider.scopes())
                .redirectUri("{baseUrl}" + ApiRoutes.OIDC_CALLBACK + "/{registrationId}")
                .clientSettings(settings)
                .build();
    }
}
