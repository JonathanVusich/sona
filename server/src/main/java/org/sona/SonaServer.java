package org.sona;

import org.sona.config.properties.LibraryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LibraryProperties.class})
public class SonaServer {

    static void main(String[] args) {
        SpringApplication.run(SonaServer.class, args);
    }
}