package org.sona.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.DispatcherType;
import org.sona.auth.ActiveUserAuthorizationManager;
import org.sona.auth.SonaJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.sona.auth.SonaJwtConverter.ISSUER;
import static org.sona.controller.ApiRoutes.CURRENT_USER;
import static org.sona.controller.ApiRoutes.JWKS;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final JwtDecoder tokenDecoder) {
        // CSRF protection isn't needed: requests authenticate with a bearer header.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, JWKS).permitAll()
                        // A user who must change their password may still see that they must.
                        .requestMatchers(CURRENT_USER).authenticated()
                        .anyRequest().access(new ActiveUserAuthorizationManager()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(tokenDecoder)
                                .jwtAuthenticationConverter(new SonaJwtConverter())))
                .build();
    }

    /**
     * Checks the access tokens Sona issues, against its own signing key.
     */
    @Bean
    public JwtDecoder tokenDecoder(final RSAKey signingKey) throws JOSEException {
        final var decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }
}
