package org.sona.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.sona.auth.ActiveUserAuthorizationManager;
import org.sona.auth.OidcJwtConverter;
import org.sona.auth.SonaJwtConverter;
import org.sona.auth.UserAuthentication;
import org.sona.config.properties.AuthProperties;
import org.sona.config.properties.OidcProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toUnmodifiableMap;
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
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final AuthenticationManagerResolver<HttpServletRequest> tokens,
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
                        .authenticationManagerResolver(tokens)
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Checks access tokens. Each issuer Sona trusts, itself and every configured OIDC provider, has its own key set
     * and rules, chosen by the token's {@code iss} claim. Tokens from any other issuer are rejected.
     */
    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> tokenAuthentication(final RSAKey signingKey,
                                                                                 final AuthProperties properties)
            throws JOSEException {
        final var sona = entry(ISSUER, sonaTokens(signingKey));
        final var providers = properties.oidcProviders().stream()
                .map(provider -> entry(provider.issuerUri(), providerTokens(provider)));
        final Map<String, AuthenticationManager> managers = Stream.concat(Stream.of(sona), providers)
                .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    private static AuthenticationManager sonaTokens(final RSAKey signingKey) throws JOSEException {
        final var decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return manager(decoder, new SonaJwtConverter());
    }

    private static AuthenticationManager providerTokens(final OidcProvider provider) {
        // Discovered on first use, so Sona still starts while a provider is down.
        final var decoder = new SupplierJwtDecoder(() -> providerDecoder(provider));
        return manager(decoder, new OidcJwtConverter(provider));
    }

    private static JwtDecoder providerDecoder(final OidcProvider provider) {
        final var decoder = NimbusJwtDecoder.withIssuerLocation(provider.issuerUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(provider.issuerUri()),
                new JwtAudienceValidator(provider.audience())));
        return decoder;
    }

    private static AuthenticationManager manager(final JwtDecoder decoder,
                                                 final Converter<Jwt, UserAuthentication> converter) {
        final var provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }
}
