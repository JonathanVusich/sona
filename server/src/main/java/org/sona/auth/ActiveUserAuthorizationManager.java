package org.sona.auth;

import org.sona.model.enums.AccountState;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Allows requests only from users whose account is active, so a user who must change their password can't do
 * anything else first.
 */
public final class ActiveUserAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(final Supplier<? extends Authentication> authentication,
                                         final RequestAuthorizationContext context) {
        return switch (authentication.get()) {
            case UserAuthentication user -> new AuthorizationDecision(user.user().state() == AccountState.ACTIVE);
            case null, default -> new AuthorizationDecision(false);
        };
    }
}
