package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.exception.UsernameTakenException;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAccountRegistrar implements AccountRegistrar {

    private final UserDao userDao;
    private final TokenIssuer tokenIssuer;
    private final RegistrationTokenService registrationTokens;

    @Override
    public OidcSignIn signIn(final ProviderUser providerUser, final String providerRefreshToken) {
        final var user = userDao.loadById(providerUser.userId()).orElse(null);
        if (user == null) {
            return new OidcSignIn.RegistrationRequired(registrationTokens.issueToken(providerUser));
        }
        return switch (user.state()) {
            case ACTIVE -> {
                final var current = followProviderRole(user, providerUser.role());
                yield new OidcSignIn.SignedIn(tokenIssuer.startSession(current, providerRefreshToken));
            }
            case DISABLED -> new OidcSignIn.Disabled();
        };
    }

    @Override
    public ProviderUser registerUser(final String registrationToken, final String username) {
        final var user = registrationTokens.readToken(registrationToken);
        final var account = new Users(user.userId(), UserType.OIDC, username, user.role(), AccountState.ACTIVE);
        // IDs and usernames are unique, so the database settles two sign-ups at once.
        if (!userDao.insert(account)) {
            throw new UsernameTakenException(account.username());
        }
        userDao.insertOidcIdentity(new OidcIdentity(user.userId(), user.issuer(), user.subject()));
        return user;
    }

    /**
     * @return the user with the role their provider gives them now, stored if it changed
     */
    private Users followProviderRole(final Users user, final UserRole role) {
        if (user.role() == role) {
            return user;
        }
        userDao.setRole(user.userId(), role);
        return new Users(user.userId(), user.type(), user.username(), role, user.state());
    }
}
