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
 * Lets users sign in through a {@link FakeOidcProvider}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAuthConfig {

    @Bean(destroyMethod = "stopServer")
    FakeOidcProvider oidcProvider() throws JOSEException {
        return new FakeOidcProvider();
    }

    @Bean
    @Primary
    AuthProperties trustFakeOidcProvider(final FakeOidcProvider oidcProvider) {
        final var provider = new OidcProvider(FakeOidcProvider.ID, FakeOidcProvider.NAME, oidcProvider.issuer(),
                FakeOidcProvider.CLIENT_ID, FakeOidcProvider.CLIENT_SECRET, List.of("openid", "offline_access"),
                "groups", List.of(FakeOidcProvider.ADMIN_GROUP));
        return new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), "/", List.of(provider));
    }
}
