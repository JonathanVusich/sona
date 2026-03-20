package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationProperties("musicbrainz")
@ConfigurationPropertiesScan
public record MusicBrainzProperties(String url) {

}
