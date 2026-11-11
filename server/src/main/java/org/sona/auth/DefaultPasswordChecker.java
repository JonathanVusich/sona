package org.sona.auth;

import org.sona.db.LocalAccount;
import org.sona.db.OidcAccount;
import org.sona.db.UserDao;
import org.sona.exception.IncorrectPasswordException;
import org.sona.exception.InvalidCredentialsException;
import org.sona.exception.PasswordManagedByProviderException;
import org.sona.exception.UnchangedPasswordException;
import org.sona.model.enums.AccountState;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DefaultPasswordChecker implements PasswordChecker {

    private final UserDao userDao;
    private final PasswordEncoder encoder;
    private final TokenIssuer tokenIssuer;
    /**
     * Checked against when the user has no password, so refusing an unknown user takes as long as refusing a wrong
     * password, and response times don't reveal which usernames exist.
     */
    private final String unknownUserHash;

    public DefaultPasswordChecker(final UserDao userDao, final PasswordEncoder encoder, final TokenIssuer tokenIssuer) {
        this.userDao = userDao;
        this.encoder = encoder;
        this.tokenIssuer = tokenIssuer;
        this.unknownUserHash = encoder.encode("unknown user");
    }

    @Override
    public LocalAccount checkLogin(final String username, final String password) {
        final var account = userDao.loadByUsername(username).orElse(null);
        return switch (account) {
            case null -> refuseWithoutPassword(password);
            // Users who sign in through an OIDC provider have no password, so they can't log in here.
            case OidcAccount _ -> refuseWithoutPassword(password);
            case LocalAccount local -> checkPassword(local, password);
        };
    }

    @Override
    public void changePassword(final SignedInUser signedIn, final String currentPassword, final String newPassword) {
        final var account = userDao.loadById(signedIn.userId()).orElseThrow();
        switch (account) {
            case LocalAccount local -> changeLocalPassword(local, currentPassword, newPassword);
            case OidcAccount _ -> throw new PasswordManagedByProviderException();
        }
    }

    private LocalAccount refuseWithoutPassword(final String password) {
        encoder.matches(password, unknownUserHash);
        throw new InvalidCredentialsException();
    }

    private LocalAccount checkPassword(final LocalAccount account, final String password) {
        // The password is checked first, so a disabled account takes as long to refuse as any other.
        final var matches = encoder.matches(password, account.password().passwordHash());
        if (!matches || account.user().state() == AccountState.DISABLED) {
            throw new InvalidCredentialsException();
        }
        return account;
    }

    private void changeLocalPassword(
            final LocalAccount account, final String currentPassword, final String newPassword) {
        if (!encoder.matches(currentPassword, account.password().passwordHash())) {
            throw new IncorrectPasswordException();
        }
        if (newPassword.equals(currentPassword)) {
            throw new UnchangedPasswordException();
        }
        userDao.setPasswordHash(account.user().userId(), encoder.encode(newPassword));
        tokenIssuer.endAllSessions(account.user());
    }
}
