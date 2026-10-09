package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.LocalUserDao;
import org.sona.db.OidcUserDao;
import org.sona.model.tables.pojos.OidcUser;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class DefaultUserDirectory implements UserDirectory {

    private final LocalUserDao localUserDao;
    private final OidcUserDao oidcUserDao;

    @Override
    public OidcUser resolve(final SignedInUser.Oidc user) {
        final var oidcUser = new OidcUser(user.userId(), user.issuer(), user.subject(), username(user), user.role());
        return oidcUserDao.upsert(oidcUser);
    }

    /**
     * Usernames are unique across local and OIDC users, so no two users look the same.
     *
     * @return the username from the provider, or one built from the subject if a different user already has it
     */
    private String username(final SignedInUser.Oidc user) {
        final var takenByOidcUser = oidcUserDao.findByUsername(user.username())
                .filter(holder -> !holder.userId().equals(user.userId()))
                .isPresent();
        if (!takenByOidcUser && !localUserDao.exists(user.username())) {
            return user.username();
        }
        final var host = URI.create(user.issuer()).getHost();
        return user.subject() + "@" + host;
    }
}
