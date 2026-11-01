package org.sona.auth;

import static org.sona.auth.OidcUtils.refreshGrantRequest;

import lombok.RequiredArgsConstructor;
import org.sona.db.SessionDao;
import org.sona.db.UserDao;
import org.sona.model.tables.pojos.OidcSession;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultProviderClient implements ProviderClient {

    private final UserDao userDao;
    private final SessionDao sessionDao;
    private final OidcDiscoveryClient discoveryClient;
    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> tokenClient =
            new RestClientRefreshTokenTokenResponseClient();

    @Override
    public boolean refreshSession(final OidcSession session) {
        final var identity = userDao.loadOidcIdentity(session.userId()).orElseThrow();
        // A provider that has been removed from the configuration no longer vouches for anyone.
        final var registration = discoveryClient.findByIssuer(identity.issuer()).orElse(null);
        if (registration == null) {
            return false;
        }
        final var response = tokenClient.getTokenResponse(refreshGrantRequest(registration, session));
        final var rotated = response.getRefreshToken();
        if (rotated != null) {
            sessionDao.upsertOidcSession(
                    new OidcSession(session.familyId(), session.userId(), rotated.getTokenValue()));
        }
        return true;
    }
}
