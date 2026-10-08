package org.sona.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.sona.config.properties.LibraryProperties;
import org.sona.config.properties.MusicBrainzProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Points the library at an in-memory filesystem and MusicBrainz at a WireMock server that serves the stubs in
 * src/test/resources/wiremock.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestLibraryConfig {

    @Bean
    @Primary
    LibraryProperties inMemoryLibrary() {
        final var fs = Jimfs.newFileSystem(Configuration.unix());
        return new LibraryProperties(fs.getPath("/ingest"), fs.getPath("/media"));
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
}
