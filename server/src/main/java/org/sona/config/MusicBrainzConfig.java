package org.sona.config;

import lombok.RequiredArgsConstructor;
import org.sona.config.properties.MusicBrainzProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration
@RequiredArgsConstructor
public class MusicBrainzConfig {

    @Bean
    public HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    public UriBuilderFactory uriBuilderFactory(MusicBrainzProperties properties) {
        final var factory = new DefaultUriBuilderFactory(properties.url());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        return factory;
    }
}
