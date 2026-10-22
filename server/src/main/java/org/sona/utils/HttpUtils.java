package org.sona.utils;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class HttpUtils {

    /**
     * Reads what's left of the request's body and throws it away, so a refusal is only answered once the client has
     * sent all of it. A client still sending when the answer arrives may lose it: a reverse proxy (Vite's, Caddy,
     * Traefik) can close the connection mid-upload once Sona's answer is through, and the browser then reports a
     * failed request rather than the answer.
     */
    public static void discardRequestBody(final HttpServletRequest request) throws IOException {
        try (final var body = request.getInputStream()) {
            body.transferTo(OutputStream.nullOutputStream());
        }
    }
}
