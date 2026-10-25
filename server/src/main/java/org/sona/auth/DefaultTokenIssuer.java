package org.sona.auth;

import static org.sona.auth.SonaJwtConverter.ISSUER;
import static org.sona.auth.SonaJwtConverter.ROLE_CLAIM;
import static org.sona.auth.SonaJwtConverter.STATE_CLAIM;
import static org.sona.auth.SonaJwtConverter.USERNAME_CLAIM;
import static org.sona.utils.HashUtils.sha256Hex;
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
import org.sona.db.SessionDao;
import org.sona.db.UserDao;
import org.sona.exception.InvalidRefreshTokenException;
import org.sona.model.enums.AccountState;
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
    public Tokens startSession(final Users user) {
        return issueTokens(user, uuidv7());
    }

    @Override
    public Tokens startSession(final Users user, final String providerRefreshToken) {
        final var familyId = uuidv7();
        sessionDao.upsertOidcSession(new OidcSession(familyId, user.userId(), providerRefreshToken));
        return issueTokens(user, familyId);
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
        // Checked here because access tokens are trusted as they are until they expire.
        final var user = userDao.loadById(used.userId())
                .filter(found -> found.state() == AccountState.ACTIVE)
                .orElse(null);
        if (user == null || !stillAllowed(user, used.familyId())) {
            endFamily(used.familyId());
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user, used.familyId());
    }

    @Override
    public void endSession(final String refreshToken) {
        final var tokenHash = sha256Hex(refreshToken);
        final var used = sessionDao.revokeIfValid(tokenHash).orElse(null);
        if (used == null) {
            revokeFamilyIfReused(tokenHash);
            throw new InvalidRefreshTokenException();
        }
        endFamily(used.familyId());
    }

    @Override
    public void endAllSessions(final Users user) {
        sessionDao.revokeAll(user.userId());
    }

    private Tokens issueTokens(final Users user, final UUID familyId) {
        final var refreshToken = randomToken();
        final var expiresAt =
                Instant.now().plus(properties.refreshTokenLifetime()).atOffset(ZoneOffset.UTC);
        sessionDao.insertRefreshToken(
                new RefreshToken(uuidv7(), user.userId(), familyId, sha256Hex(refreshToken), expiresAt, null));
        return new Tokens(accessToken(user), refreshToken);
    }

    /**
     * @return whether the user may keep their session: always for a local user, and for a user from an OIDC provider
     * only while the provider still accepts the session's refresh token
     */
    private boolean stillAllowed(final Users user, final UUID familyId) {
        return switch (user.type()) {
            case LOCAL -> true;
            case OIDC ->
                sessionDao
                        .loadOidcSession(familyId)
                        .map(providerClient::refreshSession)
                        .orElse(false);
        };
    }

    private void endFamily(final UUID familyId) {
        sessionDao.revokeFamily(familyId);
        sessionDao.deleteOidcSession(familyId);
    }

    private String accessToken(final Users user) {
        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.userId().toString())
                .claim(USERNAME_CLAIM, user.username())
                .claim(ROLE_CLAIM, user.role().getLiteral())
                .claim(STATE_CLAIM, sessionState(user).name())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenLifetime()))
                .build();
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private SessionState sessionState(final Users user) {
        return switch (user.type()) {
            case LOCAL -> sessionState(userDao.loadPassword(user.userId()).orElseThrow());
            // The provider manages the password.
            case OIDC -> SessionState.ACTIVE;
        };
    }

    private static SessionState sessionState(final Password password) {
        return switch (password.state()) {
            case VALID -> SessionState.ACTIVE;
            case CHANGE_REQUIRED -> SessionState.PASSWORD_CHANGE_REQUIRED;
        };
    }

    private void revokeFamilyIfReused(final String tokenHash) {
        sessionDao
                .loadRefreshTokenByHash(tokenHash)
                .filter(token -> token.revokedAt() != null)
                .ifPresent(token -> endFamily(token.familyId()));
    }

    private static String randomToken() {
        final var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
