package org.sona.auth;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.sona.auth.SonaJwtConverter.ISSUER;
import static org.sona.auth.SonaJwtConverter.ROLE_CLAIM;
import static org.sona.auth.SonaJwtConverter.STATE_CLAIM;
import static org.sona.auth.SonaJwtConverter.USERNAME_CLAIM;
import static org.sona.utils.IDGenerator.uuidv7;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.config.properties.AuthProperties;
import org.sona.db.Account;
import org.sona.db.LocalAccount;
import org.sona.db.OidcAccount;
import org.sona.db.SessionDao;
import org.sona.db.UsedToken;
import org.sona.db.UserDao;
import org.sona.exception.InvalidRefreshTokenException;
import org.sona.model.tables.pojos.OidcSession;
import org.sona.model.tables.pojos.Password;
import org.sona.model.tables.pojos.RefreshToken;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultTokenIssuer implements TokenIssuer {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * How long a replaced refresh token still refreshes its session. A browser loses the answer to a refresh when its
     * page is reloaded or closed while it waits, after Sona has already replaced the token, and then sends the old one
     * again. Reuse after this is taken for a stolen token.
     */
    private static final Duration LOST_REFRESH_WINDOW = Duration.ofSeconds(30);

    private final JwtEncoder encoder;
    private final SessionDao sessionDao;
    private final UserDao userDao;
    private final ProviderClient providerClient;
    private final AuthProperties properties;

    @Override
    public Tokens startSession(final LocalAccount account) {
        final var refreshToken = randomToken();
        sessionDao.insert(refreshTokenRow(account.user(), uuidv7(), refreshToken));
        return new Tokens(accessToken(account.user(), sessionState(account)), refreshToken);
    }

    @Override
    public Tokens startSession(final Users user, final String providerRefreshToken) {
        final var familyId = uuidv7();
        final var refreshToken = randomToken();
        sessionDao.insert(
                refreshTokenRow(user, familyId, refreshToken),
                new OidcSession(familyId, user.userId(), providerRefreshToken));
        // The provider manages the password.
        return new Tokens(accessToken(user, SessionState.ACTIVE), refreshToken);
    }

    @Override
    public Tokens refreshSession(final String refreshToken) {
        final var tokenHash = sha256Hex(refreshToken);
        final var replacedAfter = OffsetDateTime.now().minus(LOST_REFRESH_WINDOW);
        // A token whose replacement was lost refreshes the session in place of the token that replaced it.
        final var used = sessionDao
                .revokeIfValid(tokenHash)
                .or(() -> sessionDao.revokeFamilyTokenIfRecentlyReplaced(tokenHash, replacedAfter))
                .orElse(null);
        if (used == null) {
            revokeFamilyIfReused(tokenHash);
            throw new InvalidRefreshTokenException();
        }
        final var familyId = used.token().familyId();
        // Checked here because access tokens are trusted as they are until they expire.
        final var account = userDao.loadActiveById(used.token().userId()).orElse(null);
        if (account == null || !stillAllowed(account, used)) {
            sessionDao.revokeFamily(familyId);
            throw new InvalidRefreshTokenException();
        }
        final var newToken = randomToken();
        sessionDao.insert(refreshTokenRow(account.user(), familyId, newToken));
        return new Tokens(accessToken(account.user(), sessionState(account)), newToken);
    }

    @Override
    public void endSession(final String refreshToken) {
        final var tokenHash = sha256Hex(refreshToken);
        final var used = sessionDao.revokeIfValid(tokenHash).orElse(null);
        if (used == null) {
            revokeFamilyIfReused(tokenHash);
            throw new InvalidRefreshTokenException();
        }
        sessionDao.revokeFamily(used.token().familyId());
    }

    @Override
    public void endAllSessions(final Users user) {
        sessionDao.revokeAll(user.userId());
    }

    private RefreshToken refreshTokenRow(final Users user, final UUID familyId, final String refreshToken) {
        final var expiresAt =
                Instant.now().plus(properties.refreshTokenLifetime()).atOffset(ZoneOffset.UTC);
        return new RefreshToken(uuidv7(), user.userId(), familyId, sha256Hex(refreshToken), expiresAt, null);
    }

    /**
     * @return whether the user may keep their session: always for a local user, and for a user from an OIDC provider
     * only while the provider still accepts the session's refresh token
     */
    private boolean stillAllowed(final Account account, final UsedToken used) {
        return switch (account) {
            case LocalAccount _ -> true;
            case OidcAccount oidc -> {
                final var providerRefreshToken = used.providerRefreshToken();
                if (providerRefreshToken == null) {
                    yield false;
                }
                final var token = used.token();
                final var session = new OidcSession(token.familyId(), token.userId(), providerRefreshToken);
                yield providerClient.refreshSession(oidc.identity(), session);
            }
        };
    }

    private String accessToken(final Users user, final SessionState state) {
        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.userId().toString())
                .claim(USERNAME_CLAIM, user.username())
                .claim(ROLE_CLAIM, user.role().getLiteral())
                .claim(STATE_CLAIM, state.name())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenLifetime()))
                .build();
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static SessionState sessionState(final Account account) {
        return switch (account) {
            case LocalAccount local -> sessionState(local.password());
            // The provider manages the password.
            case OidcAccount _ -> SessionState.ACTIVE;
        };
    }

    private static SessionState sessionState(final Password password) {
        return switch (password.state()) {
            case VALID -> SessionState.ACTIVE;
            case CHANGE_REQUIRED -> SessionState.PASSWORD_CHANGE_REQUIRED;
        };
    }

    private void revokeFamilyIfReused(final String tokenHash) {
        sessionDao.loadRevokedByHash(tokenHash).ifPresent(token -> sessionDao.revokeFamily(token.familyId()));
    }

    private static String randomToken() {
        final var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
