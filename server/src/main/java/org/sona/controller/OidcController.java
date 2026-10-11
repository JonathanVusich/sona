package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.auth.OidcClientRegistrations;
import org.sona.controller.response.OidcProviderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The OIDC providers users can sign in through.
 */
@RestController
@RequiredArgsConstructor
public final class OidcController {

    private final OidcClientRegistrations registrations;

    @GetMapping(ApiRoutes.OIDC_PROVIDERS)
    public List<OidcProviderResponse> providers() {
        return registrations.providers().stream()
                .map(provider -> new OidcProviderResponse(provider.id(), provider.name()))
                .toList();
    }
}
