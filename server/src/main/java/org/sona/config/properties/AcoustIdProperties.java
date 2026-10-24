package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url base URL of the AcoustID v2 API
 * @param apiKey AcoustID's key for Sona, which identifies the application looking fingerprints up
 */
@ConfigurationProperties("acoustid")
public record AcoustIdProperties(String url, String apiKey) {}
