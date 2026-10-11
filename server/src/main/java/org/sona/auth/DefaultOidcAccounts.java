package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.db.OidcIdentityDao;
import org.sona.db.UserDao;
import org.sona.exception.ClientException;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.OidcIdentity;
import org.sona.model.tables.pojos.Users;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultOidcAccounts implements OidcAccounts {

    private final UserDao userDao;
    private final OidcIdentityDao oidcIdentityDao;
    private final TokenService tokenService;
    private final RegistrationTokens registrationTokens;

    @Override
    public OidcSignIn signIn(final ProviderUser providerUser, final String providerRefreshToken) {
        final var user = userDao.find(providerUser.userId()).orElse(null);
        if (user == null) {
            return new OidcSignIn.RegistrationRequired(registrationTokens.issue(providerUser));
        }
        return switch (user.state()) {
            case ACTIVE -> {
                final var current = followProviderRole(user, providerUser.role());
                yield new OidcSignIn.SignedIn(tokenService.issue(current, providerRefreshToken));
            }
            case DISABLED -> new OidcSignIn.Disabled();
        };
    }

    @Override
    public ProviderUser register(final String registrationToken, final String username) {
        final var user = registrationTokens.read(registrationToken)
                .orElseThrow(() -> new ClientException(ErrorCode.INVALID_REGISTRATION_TOKEN));
        if (username == null || username.isBlank()) {
            throw new ClientException(ErrorCode.INVALID_USERNAME);
        }
        if (userDao.find(user.userId()).isPresent()) {
            throw new ClientException(ErrorCode.ALREADY_REGISTERED);
        }
        final var account = new Users(user.userId(), UserType.OIDC, username, user.role(), AccountState.ACTIVE);
        try {
            userDao.insert(account);
        } catch (final DuplicateKeyException _) {
            // Usernames are unique, so the database settles two users choosing the same one at once.
            throw new ClientException(ErrorCode.USERNAME_TAKEN);
        }
        oidcIdentityDao.insert(new OidcIdentity(user.userId(), user.issuer(), user.subject()));
        return user;
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
