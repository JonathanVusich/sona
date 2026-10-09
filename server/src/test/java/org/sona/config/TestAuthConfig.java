package org.sona.config;

import com.nimbusds.jose.JOSEException;
import org.sona.config.properties.AuthProperties;
import org.sona.config.properties.OidcProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

/**
 * Makes Sona trust tokens from a {@link FakeOidcProvider}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAuthConfig {

    @Bean(destroyMethod = "stop")
    FakeOidcProvider oidcProvider() throws JOSEException {
        return new FakeOidcProvider();
    }

    @Bean
    @Primary
    AuthProperties trustFakeOidcProvider(final FakeOidcProvider oidcProvider) {
        final var provider = new OidcProvider(oidcProvider.issuer(), FakeOidcProvider.AUDIENCE, "preferred_username",
                "groups", List.of(FakeOidcProvider.ADMIN_GROUP));
        return new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), List.of(provider));
    }
}
