package org.sona.auth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Writes an {@link ErrorResponse} from the security filters, which refuse requests before any controller runs.
 */
@Component
@RequiredArgsConstructor
public final class ErrorResponseWriter {

    private final JsonMapper mapper;

    public void write(final HttpServletResponse response, final ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(code));
    }
}
