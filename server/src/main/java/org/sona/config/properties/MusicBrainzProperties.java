package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param url             base URL of the MusicBrainz WS/2 API (or a local mirror)
 * @param requestInterval minimum time between requests; MusicBrainz rate limits clients to ~1 request/second
 */
@ConfigurationProperties("musicbrainz")
public record MusicBrainzProperties(
        String url,
        @DefaultValue("1s") Duration requestInterval
) {
}
