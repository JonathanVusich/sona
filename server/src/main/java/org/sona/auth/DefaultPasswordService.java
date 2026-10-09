package org.sona.auth;

import org.sona.db.LocalUserDao;
import org.sona.model.enums.AccountState;
import org.sona.model.tables.pojos.LocalUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DefaultPasswordService implements PasswordService {

    private final LocalUserDao localUserDao;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;
    /**
     * Checked against when the username is unknown, so refusing an unknown user takes as long as refusing a wrong
     * password, and response times don't reveal which usernames exist.
     */
    private final String unknownUserHash;

    public DefaultPasswordService(final LocalUserDao localUserDao, final PasswordEncoder encoder,
                                  final TokenService tokenService) {
        this.localUserDao = localUserDao;
        this.encoder = encoder;
        this.tokenService = tokenService;
        this.unknownUserHash = encoder.encode("unknown user");
    }

    @Override
    public Optional<LocalUser> authenticate(final String username, final String password) {
        final var user = localUserDao.findByUsername(username).orElse(null);
        if (user == null) {
            encoder.matches(password, unknownUserHash);
            return Optional.empty();
        }
        // The password is checked first, so a disabled account takes as long to refuse as any other.
        if (!encoder.matches(password, user.passwordHash()) || user.state() == AccountState.DISABLED) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    @Override
    public PasswordChange change(final SignedInUser.Local signedIn, final String currentPassword, final String newPassword) {
        final var user = localUserDao.find(signedIn.userId()).orElseThrow();
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            return PasswordChange.INCORRECT_CURRENT_PASSWORD;
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.equals(currentPassword)) {
            return PasswordChange.INVALID_NEW_PASSWORD;
        }
        localUserDao.updatePassword(user.userId(), encoder.encode(newPassword));
        tokenService.revokeAll(user);
        return PasswordChange.CHANGED;
    }
}
