package org.sona.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.nio.file.Path;

@ConfigurationProperties("library")
@ConfigurationPropertiesScan
public record LibraryProperties(Path ingestFolder, Path mediaFolder) {
}
