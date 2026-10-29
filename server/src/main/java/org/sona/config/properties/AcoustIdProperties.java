package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url base URL of the AcoustID v2 API
 */
@ConfigurationProperties("acoustid")
public record AcoustIdProperties(String url) {}
