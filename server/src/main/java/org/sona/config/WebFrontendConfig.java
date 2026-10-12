package org.sona.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.sona.controller.ApiRoutes.API;

/**
 * Serves the web frontend, which the boot jar bundles under {@code static/}, from the same origin as the API, as the
 * refresh cookie needs. Without a bundled frontend, as under bootRun and most tests, there's nothing to serve.
 */
@Configuration
@ConditionalOnResource(resources = WebFrontendConfig.INDEX)
@RequiredArgsConstructor
public class WebFrontendConfig implements WebMvcConfigurer {

    private static final String LOCATION = "classpath:/static/";
    // Not private, as the class's own annotation reads it.
    static final String INDEX = LOCATION + "index.html";

    private final ResourceLoader resourceLoader;

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        // A handler with no locations finds nothing, so an unknown endpoint is still a 404 rather than the frontend.
        registry.addResourceHandler(API + "/*/**");
        final var index = resourceLoader.getResource(INDEX);
        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATION)
                .resourceChain(true)
                .addResolver(new SinglePageResolver(index));
    }
}
