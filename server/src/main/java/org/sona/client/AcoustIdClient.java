package org.sona.client;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.sona.client.model.acoustid.LookupResponse;
import org.sona.config.properties.AcoustIdProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class AcoustIdClient {

    private final RestClient acoustIdRestClient;
    private final AcoustIdProperties properties;

    /**
     * Looks up the MusicBrainz recordings whose fingerprints are like this one.
     *
     * @param fingerprint compressed, as {@code fpcalc} prints it
     * @param duration of the whole file, which AcoustID takes in whole seconds
     */
    // AcoustID allows three requests a second; see resilience4j.ratelimiter.instances.acoustid.
    @RateLimiter(name = "acoustid")
    public LookupResponse lookup(final String fingerprint, final Duration duration) {
        // Sent as a form rather than in the URL, as AcoustID prefers, since a fingerprint runs to a few kilobytes.
        final var form = new LinkedMultiValueMap<String, String>();
        form.add("client", properties.apiKey());
        form.add("meta", "recordingids");
        form.add("duration", Long.toString(duration.toSeconds()));
        form.add("fingerprint", fingerprint);
        return acoustIdRestClient
                .post()
                .uri("lookup")
                .contentType(APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(LookupResponse.class);
    }
}
