package org.sona.auth;

import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.exception.IncorrectPasswordException;
import org.sona.exception.InvalidCredentialsException;
import org.sona.exception.PasswordManagedByProviderException;
import org.sona.exception.UnchangedPasswordException;
import org.sona.model.enums.AccountState;
import org.sona.model.tables.pojos.Users;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DefaultPasswordChecker implements PasswordChecker {

    private final UserDao userDao;
    private final PasswordDao passwordDao;
    private final PasswordEncoder encoder;
    private final TokenIssuer tokenIssuer;
    /**
     * Checked against when the user has no password, so refusing an unknown user takes as long as refusing a wrong
     * password, and response times don't reveal which usernames exist.
     */
    private final String unknownUserHash;

    public DefaultPasswordChecker(
            final UserDao userDao,
            final PasswordDao passwordDao,
            final PasswordEncoder encoder,
            final TokenIssuer tokenIssuer) {
        this.userDao = userDao;
        this.passwordDao = passwordDao;
        this.encoder = encoder;
        this.tokenIssuer = tokenIssuer;
        this.unknownUserHash = encoder.encode("unknown user");
    }

    @Override
    public Users checkLogin(final String username, final String password) {
        final var user = userDao.loadByUsername(username).orElse(null);
        if (user == null) {
            encoder.matches(password, unknownUserHash);
            throw new InvalidCredentialsException();
        }
        // Users who sign in through an OIDC provider have no password, so they can't log in here.
        final var stored = passwordDao.loadByUserId(user.userId()).orElse(null);
        if (stored == null) {
            encoder.matches(password, unknownUserHash);
            throw new InvalidCredentialsException();
        }
        // The password is checked first, so a disabled account takes as long to refuse as any other.
        if (!encoder.matches(password, stored.passwordHash()) || user.state() == AccountState.DISABLED) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    @Override
    public void changePassword(final SignedInUser signedIn, final String currentPassword, final String newPassword) {
        final var user = userDao.loadById(signedIn.userId()).orElseThrow();
        switch (user.type()) {
            case LOCAL -> changeLocalPassword(user, currentPassword, newPassword);
            case OIDC -> throw new PasswordManagedByProviderException();
        }
    }

    private void changeLocalPassword(final Users user, final String currentPassword, final String newPassword) {
        final var password = passwordDao.loadByUserId(user.userId()).orElseThrow();
        if (!encoder.matches(currentPassword, password.passwordHash())) {
            throw new IncorrectPasswordException();
        }
        if (newPassword.equals(currentPassword)) {
            throw new UnchangedPasswordException();
        }
        passwordDao.updateHash(user.userId(), encoder.encode(newPassword));
        tokenIssuer.endAllSessions(user);
    }
}
