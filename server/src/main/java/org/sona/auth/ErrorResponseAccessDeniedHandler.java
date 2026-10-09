package org.sona.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.model.enums.AccountState;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Refuses a signed-in user's request with an error response. A user who must change their password is told that this
 * is why, so the frontend can send them to the change-password screen.
 */
@Component
@RequiredArgsConstructor
public final class ErrorResponseAccessDeniedHandler implements AccessDeniedHandler {

    // Adds the WWW-Authenticate header that RFC 6750 asks for.
    private final AccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
    private final ErrorResponseWriter writer;

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
                       final AccessDeniedException exception) throws IOException, ServletException {
        bearerHandler.handle(request, response, exception);
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        final var code = switch (authentication) {
            case UserAuthentication user when user.user().state() == AccountState.PASSWORD_CHANGE_REQUIRED ->
                    ErrorCode.PASSWORD_CHANGE_REQUIRED;
            case null, default -> ErrorCode.ACCESS_DENIED;
        };
        writer.write(response, code);
    }
}
