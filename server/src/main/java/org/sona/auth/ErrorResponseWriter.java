package org.sona.auth;

import static org.sona.utils.HttpUtils.discardRequestBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an {@link ErrorResponse} from the security filters, which refuse requests before any controller runs.
 */
@Component
@RequiredArgsConstructor
public final class ErrorResponseWriter {

    private final JsonMapper mapper;

    /**
     * Reads the rest of the request's body first, as a refused upload's hasn't been read (see
     * {@code HttpUtils.discardRequestBody}).
     */
    public void writeError(final HttpServletRequest request, final HttpServletResponse response, final ErrorCode code)
            throws IOException {
        discardRequestBody(request);
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(code));
    }
}
