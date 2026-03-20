package org.sona.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.StreamReadFeature;

@TestConfiguration
public class JacksonConfig {

    @Primary
    @Bean
    public JsonMapperBuilderCustomizer mapperCustomizer() {
        return builder -> builder.configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, true);
    }
}
