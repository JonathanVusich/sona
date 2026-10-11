package org.sona.auth;

import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DefaultPasswordService implements PasswordService {

    private final UserDao userDao;
    private final PasswordDao passwordDao;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;
    /**
     * Checked against when the user has no password, so refusing an unknown user takes as long as refusing a wrong
     * password, and response times don't reveal which usernames exist.
     */
    private final String unknownUserHash;

    public DefaultPasswordService(final UserDao userDao, final PasswordDao passwordDao, final PasswordEncoder encoder,
                                  final TokenService tokenService) {
        this.userDao = userDao;
        this.passwordDao = passwordDao;
        this.encoder = encoder;
        this.tokenService = tokenService;
        this.unknownUserHash = encoder.encode("unknown user");
    }

    @Override
    public Optional<Users> authenticate(final String username, final String password) {
        final var user = userDao.findByUsername(username).orElse(null);
        if (user == null) {
            encoder.matches(password, unknownUserHash);
            return Optional.empty();
        }
        // Users who sign in through an OIDC provider have no password, so they can't log in here.
        final var stored = passwordDao.find(user.userId()).orElse(null);
        if (stored == null) {
            encoder.matches(password, unknownUserHash);
            return Optional.empty();
        }
        // The password is checked first, so a disabled account takes as long to refuse as any other.
        if (!encoder.matches(password, stored.passwordHash()) || user.state() == AccountState.DISABLED) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    @Override
    public PasswordChange change(final SignedInUser signedIn, final String currentPassword, final String newPassword) {
        final var user = userDao.find(signedIn.userId()).orElseThrow();
        return switch (user.type()) {
            case LOCAL -> change(user, currentPassword, newPassword);
            case OIDC -> PasswordChange.MANAGED_BY_PROVIDER;
        };
    }

    private PasswordChange change(final Users user, final String currentPassword, final String newPassword) {
        final var password = passwordDao.find(user.userId()).orElseThrow();
        if (!encoder.matches(currentPassword, password.passwordHash())) {
            return PasswordChange.INCORRECT_CURRENT_PASSWORD;
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.equals(currentPassword)) {
            return PasswordChange.INVALID_NEW_PASSWORD;
        }
        passwordDao.update(user.userId(), encoder.encode(newPassword));
        tokenService.revokeAll(user);
        return PasswordChange.CHANGED;
    }
}
