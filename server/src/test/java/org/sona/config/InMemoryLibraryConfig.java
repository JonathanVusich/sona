package org.sona.config;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

import java.nio.file.FileSystem;
import java.nio.file.Path;

@Primary
@ConfigurationProperties("library")
@Getter
public final class InMemoryLibraryConfig {

    private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    private final Path ingestFolder = fs.getPath("/ingest");
    private final Path mediaFolder = fs.getPath("/media");
}
