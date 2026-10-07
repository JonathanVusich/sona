package org.sona.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.RequiredArgsConstructor;
import org.sona.config.properties.MusicBrainzProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class MusicBrainzConfig {

    @Bean
    public UriBuilderFactory uriBuilderFactory(MusicBrainzProperties properties) {
        final var factory = new DefaultUriBuilderFactory(properties.url());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        return factory;
    }

    @Bean
    public RestClient musicBrainzRestClient(UriBuilderFactory uriBuilderFactory,
                                            JsonMapper mapper,
                                            MusicBrainzProperties properties) {
        final var rateLimiter = RateLimiter.of("musicbrainz", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(properties.requestInterval())
                .build());

        return RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .configureMessageConverters(converters -> converters
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)))
                .requestInterceptor((request, body, execution) -> {
                    RateLimiter.waitForPermission(rateLimiter);
                    return execution.execute(request, body);
                })
                .build();
    }
}
