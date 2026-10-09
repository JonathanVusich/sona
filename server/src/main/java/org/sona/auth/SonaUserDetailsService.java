package org.sona.auth;

import lombok.RequiredArgsConstructor;
import org.sona.db.LocalUserDao;
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

    private final LocalUserDao localUserDao;

    @Override
    public UserDetails loadUserByUsername(final String username) {
        final var user = localUserDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .disabled(user.state() == AccountState.DISABLED)
                .build();
    }
}
