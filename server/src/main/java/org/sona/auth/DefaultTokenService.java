package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.AuthProperties;
import org.sona.db.RefreshTokenDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.tables.pojos.RefreshToken;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.sona.utils.HashUtils.sha256Hex;
import static org.sona.utils.IDGenerator.uuidv7;

@Service
@RequiredArgsConstructor
public class DefaultTokenService implements TokenService {

    /**
     * The {@code iss} claim of the access tokens Sona issues.
     */
    public static final String ISSUER = "sona";

    public static final String USERNAME_CLAIM = "preferred_username";
    public static final String ROLE_CLAIM = "role";
    public static final String STATE_CLAIM = "account_state";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final RefreshTokenDao refreshTokenDao;
    private final UserDao userDao;
    private final AuthProperties properties;

    @Override
    public Tokens issue(final Users user) {
        return issue(user, uuidv7());
    }

    @Override
    public Optional<Tokens> refresh(final String refreshToken) {
        final var tokenHash = sha256Hex(refreshToken);
        final var used = refreshTokenDao.revokeIfValid(tokenHash).orElse(null);
        if (used == null) {
            revokeFamilyIfReused(tokenHash);
            return Optional.empty();
        }
        // Checked here because access tokens are trusted as they are until they expire.
        return userDao.find(used.userId())
                .filter(user -> user.state() != AccountState.DISABLED)
                .map(user -> issue(user, used.familyId()));
    }

    @Override
    public void revoke(final String refreshToken) {
        refreshTokenDao.find(sha256Hex(refreshToken))
                .ifPresent(token -> refreshTokenDao.revokeFamily(token.familyId()));
    }

    @Override
    public void revokeAll(final Users user) {
        refreshTokenDao.revokeAll(user.userId());
    }

    private Tokens issue(final Users user, final UUID familyId) {
        final var refreshToken = randomToken();
        final var expiresAt = Instant.now().plus(properties.refreshTokenLifetime()).atOffset(ZoneOffset.UTC);
        refreshTokenDao.insert(new RefreshToken(uuidv7(), user.userId(), familyId, sha256Hex(refreshToken), expiresAt, null));
        return new Tokens(accessToken(user), refreshToken);
    }

    private String accessToken(final Users user) {
        final var now = Instant.now();
        final var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.userId().toString())
                .claim(USERNAME_CLAIM, user.username())
                .claim(ROLE_CLAIM, user.role().getLiteral())
                .claim(STATE_CLAIM, user.state().getLiteral())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenLifetime()))
                .build();
        final var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void revokeFamilyIfReused(final String tokenHash) {
        refreshTokenDao.find(tokenHash)
                .filter(token -> token.revokedAt() != null)
                .ifPresent(token -> refreshTokenDao.revokeFamily(token.familyId()));
    }

    private static String randomToken() {
        final var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
