package org.sona.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the built web frontend from Sona's origin, as the refresh cookie needs. The {@code e2eTest} Gradle task builds
 * it and passes its folder in {@code sona.e2e.web-dist}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestFrontendConfig implements WebMvcConfigurer {

    private final String webDist;

    public TestFrontendConfig(@Value("${sona.e2e.web-dist}") final String webDist) {
        this.webDist = webDist;
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + webDist + "/")
                .resourceChain(false)
                .addResolver(new SinglePageResolver());
    }

    /**
     * The frontend's files are public; the API keeps its own rules.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain frontendFilterChain(final HttpSecurity http) throws Exception {
        return http.securityMatcher(request -> !request.getRequestURI().startsWith("/api/"))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
