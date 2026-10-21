package org.sona.config.properties;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("library")
public record LibraryProperties(Path ingestFolder, Path mediaFolder) {}
