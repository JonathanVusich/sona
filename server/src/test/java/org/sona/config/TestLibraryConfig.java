package org.sona.config;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.sona.config.properties.AcoustIdProperties;
import org.sona.config.properties.LibraryProperties;
import org.sona.config.properties.MusicBrainzProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.unit.DataSize;

/**
 * Points the library at an in-memory filesystem, and MusicBrainz and AcoustID at a WireMock server that serves the stubs in
 * src/test/resources/wiremock.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestLibraryConfig {

    @Bean
    @Primary
    LibraryProperties inMemoryLibrary() {
        final var fs = Jimfs.newFileSystem(Configuration.unix());
        // Small, so a test can send a file larger than Sona accepts without filling memory.
        return new LibraryProperties(fs.getPath("/ingest"), fs.getPath("/media"), DataSize.ofMegabytes(10));
    }

    @Bean(destroyMethod = "stop")
    WireMockServer musicBrainzServer() {
        final var server = new WireMockServer(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"));
        server.start();
        return server;
    }

    @Bean
    @Primary
    MusicBrainzProperties musicBrainzStubs(final WireMockServer musicBrainzServer) {
        return new MusicBrainzProperties(musicBrainzServer.baseUrl() + "/ws/2/");
    }

    // The same server stubs AcoustID, under its own path.
    @Bean
    @Primary
    AcoustIdProperties acoustIdStubs(final WireMockServer musicBrainzServer) {
        return new AcoustIdProperties(musicBrainzServer.baseUrl() + "/v2/", "test-key");
    }
}
