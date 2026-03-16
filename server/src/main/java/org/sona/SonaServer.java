package org.sona;

import org.sona.config.LibraryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LibraryConfig.class})
public class SonaServer {
    static void main(String[] args) {
        SpringApplication.run(SonaServer.class, args);
    }
}