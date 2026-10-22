package org.sona.auth;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;

/**
 * Allows requests (and STOMP messages) only from users whose account is active, so a user who must change their
 * password can't do anything else first.
 */
public final class ActiveUserService<T> implements AuthorizationManager<T> {

    @Override
    public AuthorizationResult authorize(final Supplier<? extends Authentication> authentication, final T context) {
        return switch (authentication.get()) {
            case UserAuthentication user ->
                new AuthorizationDecision(user.user().state() == SessionState.ACTIVE);
            case null, default -> new AuthorizationDecision(false);
        };
    }
}
