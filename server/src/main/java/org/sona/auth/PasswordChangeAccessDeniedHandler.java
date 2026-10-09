package org.sona.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.sona.model.enums.AccountState;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Tells a user who must change their password that this is why they were refused, so the frontend can send them to
 * the change-password screen. Every other refusal gets Spring's usual bearer-token response.
 */
@Component
@RequiredArgsConstructor
public final class PasswordChangeAccessDeniedHandler implements AccessDeniedHandler {

    public static final String PASSWORD_CHANGE_REQUIRED = "password_change_required";

    private final AccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
    private final JsonMapper mapper;

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
                       final AccessDeniedException exception) throws IOException, ServletException {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        switch (authentication) {
            case UserAuthentication user when user.user().state() == AccountState.PASSWORD_CHANGE_REQUIRED ->
                    writePasswordChangeRequired(response);
            case null, default -> bearerHandler.handle(request, response, exception);
        }
    }

    private void writePasswordChangeRequired(final HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        final var problem = Map.of(
                "status", HttpStatus.FORBIDDEN.value(),
                "title", "Password change required",
                "code", PASSWORD_CHANGE_REQUIRED);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
