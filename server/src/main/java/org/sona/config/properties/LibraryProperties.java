package org.sona.config.properties;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param maxUploadSize the largest file Sona accepts as an upload
 */
@ConfigurationProperties("library")
public record LibraryProperties(Path ingestFolder, Path mediaFolder, DataSize maxUploadSize) {}
