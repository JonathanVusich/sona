package org.sona.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sona.db.OidcIdentityDao;
import org.sona.db.OidcSessionDao;
import org.sona.model.tables.pojos.OidcSession;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;

import static org.sona.auth.OidcUtils.refreshGrantRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProviderClient implements ProviderClient {

    private final OidcIdentityDao oidcIdentityDao;
    private final OidcSessionDao oidcSessionDao;
    private final OidcDiscoveryClient discoveryClient;
    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> tokenClient =
            new RestClientRefreshTokenTokenResponseClient();

    @Override
    public boolean refreshSession(final OidcSession session) {
        final var identity = oidcIdentityDao.loadByUserId(session.userId()).orElseThrow();
        // A provider that has been removed from the configuration no longer vouches for anyone.
        final var registration = discoveryClient.findByIssuer(identity.issuer()).orElse(null);
        if (registration == null) {
            return false;
        }
        try {
            final var response = tokenClient.getTokenResponse(refreshGrantRequest(registration, session));
            final var rotated = response.getRefreshToken();
            if (rotated != null) {
                oidcSessionDao.upsert(new OidcSession(session.familyId(), session.userId(), rotated.getTokenValue()));
            }
            return true;
        } catch (final OAuth2AuthorizationException exception) {
            // The provider refused the refresh token, or couldn't be reached. Either way it no longer vouches for the
            // user, who signs in again.
            log.info("{} didn't refresh a session: {}", registration.getRegistrationId(), exception.getError());
            return false;
        }
    }
}
