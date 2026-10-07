package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url base URL of the MusicBrainz WS/2 API (or a local mirror)
 */
@ConfigurationProperties("musicbrainz")
public record MusicBrainzProperties(
        String url
) {
}
