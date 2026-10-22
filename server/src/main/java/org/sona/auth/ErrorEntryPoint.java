package org.sona.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Refuses a request with no access token, or one that is invalid or expired, with an error response.
 */
@Component
@RequiredArgsConstructor
public final class ErrorEntryPoint implements AuthenticationEntryPoint {

    // Adds the WWW-Authenticate header that RFC 6750 asks for.
    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ErrorResponseWriter writer;

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException exception)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, exception);
        writer.writeError(request, response, ErrorCode.NOT_SIGNED_IN);
    }
}
