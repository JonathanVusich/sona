package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.model.enums.UserRole;
import org.sona.model.tables.pojos.Users;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultOidcAccounts implements OidcAccounts {

    private final UserDao userDao;
    private final TokenService tokenService;
    private final RegistrationTokens registrationTokens;

    @Override
    public OidcSignIn signIn(final ProviderUser providerUser) {
        final var user = userDao.find(providerUser.userId()).orElse(null);
        if (user == null) {
            return new OidcSignIn.RegistrationRequired(registrationTokens.issue(providerUser));
        }
        return switch (user.state()) {
            case ACTIVE -> {
                final var current = followProviderRole(user, providerUser.role());
                yield new OidcSignIn.SignedIn(tokenService.issue(current));
            }
            case DISABLED -> new OidcSignIn.Disabled();
        };
    }

    /**
     * @return the user with the role their provider gives them now, stored if it changed
     */
    private Users followProviderRole(final Users user, final UserRole role) {
        if (user.role() == role) {
            return user;
        }
        userDao.updateRole(user.userId(), role);
        return new Users(user.userId(), user.type(), user.username(), role, user.state());
    }
}
