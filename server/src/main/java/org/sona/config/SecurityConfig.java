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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.sona.auth.DefaultTokenService.ISSUER;
import static org.sona.controller.ApiRoutes.AUTH_LOGOUT;
import static org.sona.controller.ApiRoutes.AUTH_REFRESH;
import static org.sona.controller.ApiRoutes.AUTH_TOKEN;
import static org.sona.controller.ApiRoutes.CURRENT_USER;
import static org.sona.controller.ApiRoutes.CURRENT_USER_PASSWORD;
import static org.sona.controller.ApiRoutes.JWKS;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final JwtDecoder tokenDecoder,
                                                   final AuthenticationEntryPoint authenticationEntryPoint,
                                                   final AccessDeniedHandler accessDeniedHandler) {
        // CSRF protection isn't needed: requests authenticate with a bearer header, and the refresh cookie is
        // SameSite=Strict.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, AUTH_TOKEN, AUTH_REFRESH, AUTH_LOGOUT).permitAll()
                        .requestMatchers(HttpMethod.GET, JWKS).permitAll()
                        // A user who must change their password may still do so, and see that they must.
                        .requestMatchers(CURRENT_USER, CURRENT_USER_PASSWORD).authenticated()
                        .anyRequest().access(new ActiveUserAuthorizationManager()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(tokenDecoder)
                                .jwtAuthenticationConverter(new SonaJwtConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
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
