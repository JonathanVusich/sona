package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.UserDao;
import org.sona.model.enums.AccountState;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for username and password logins.
 */
@Service
@RequiredArgsConstructor
public class SonaUserDetailsService implements UserDetailsService {

    private final UserDao userDao;

    @Override
    public UserDetails loadUserByUsername(final String username) {
        // Users who only sign in through an OIDC provider have no password, so they can't log in here.
        final var user = userDao.findByUsername(username)
                .filter(found -> found.passwordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .disabled(user.state() == AccountState.DISABLED)
                .build();
    }
}
