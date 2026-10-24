package org.sona.config;

import org.sona.config.properties.AcoustIdProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class AcoustIdConfig {

    @Bean
    public RestClient acoustIdRestClient(AcoustIdProperties properties, JsonMapper mapper) {
        return RestClient.builder()
                .baseUrl(properties.url())
                .configureMessageConverters(
                        converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)))
                .build();
    }
}
