package org.sona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SonaServer {

    static void main(String[] args) {
        SpringApplication.run(SonaServer.class, args);
    }
}
