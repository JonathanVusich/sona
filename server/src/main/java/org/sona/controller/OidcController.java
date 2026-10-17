package org.sona.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sona.auth.AccountRegistrar;
import org.sona.auth.OidcDiscoveryClient;
import org.sona.controller.request.RegisterRequest;
import org.sona.controller.response.OidcProviderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Signing in through OIDC providers, beside the flow Spring runs at {@link ApiRoutes#OIDC_AUTHORIZE} and
 * {@link ApiRoutes#OIDC_CALLBACK}.
 */
@RestController
@RequiredArgsConstructor
public final class OidcController {

    private final OidcDiscoveryClient discoveryClient;
    private final AccountRegistrar accountRegistrar;

    @GetMapping(ApiRoutes.OIDC_PROVIDERS)
    public List<OidcProviderResponse> listProviders() {
        return discoveryClient.listProviders().stream()
                .map(provider -> new OidcProviderResponse(provider.id(), provider.name()))
                .toList();
    }

    /**
     * Creates the account of a user who signed in through their provider for the first time. The response points
     * the browser back to the provider, which signs them straight in if their session there is still open.
     */
    @PostMapping(ApiRoutes.AUTH_REGISTER)
    public ResponseEntity<Void> registerUser(@RequestBody @Valid final RegisterRequest request) {
        final var user = accountRegistrar.registerUser(request.registrationToken(), request.username());
        final var signIn = URI.create(ApiRoutes.OIDC_AUTHORIZE + "/" + user.providerId());
        return ResponseEntity.created(signIn).build();
    }
}
