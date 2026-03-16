package org.sona.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("library")
public record LibraryConfig(Path ingestFolder, Path mediaFolder) {
}
