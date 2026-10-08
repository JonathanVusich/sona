package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("library")
public record LibraryProperties(Path ingestFolder, Path mediaFolder) {
}
