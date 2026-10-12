package org.sona.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.sona.auth.ActiveUserService;
import org.sona.auth.OidcDiscoveryClient;
import org.sona.auth.SignInHandler;
import org.sona.auth.SilentSignInResolver;
import org.sona.auth.SonaJwtConverter;
import org.sona.config.properties.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.sona.auth.SonaJwtConverter.ISSUER;
import static org.sona.controller.ApiRoutes.API;
import static org.sona.controller.ApiRoutes.AUTH_LOGOUT;
import static org.sona.controller.ApiRoutes.AUTH_REFRESH;
import static org.sona.controller.ApiRoutes.AUTH_REGISTER;
import static org.sona.controller.ApiRoutes.AUTH_TOKEN;
import static org.sona.controller.ApiRoutes.CURRENT_USER;
import static org.sona.controller.ApiRoutes.CURRENT_USER_PASSWORD;
import static org.sona.controller.ApiRoutes.JWKS;
import static org.sona.controller.ApiRoutes.OIDC_AUTHORIZE;
import static org.sona.controller.ApiRoutes.OIDC_CALLBACK;
import static org.sona.controller.ApiRoutes.OIDC_PROVIDERS;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final JwtDecoder tokenDecoder,
                                                   final AuthenticationEntryPoint authenticationEntryPoint,
                                                   final AccessDeniedHandler accessDeniedHandler,
                                                   final OidcDiscoveryClient discoveryClient,
                                                   final OAuth2AuthorizedClientRepository authorizedClients,
                                                   final SignInHandler oidcSignIn,
                                                   final AuthProperties properties) {
        // CSRF protection isn't needed: requests authenticate with a bearer header, and the refresh cookie is
        // SameSite=Strict.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, AUTH_TOKEN, AUTH_REFRESH, AUTH_LOGOUT, AUTH_REGISTER)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, JWKS, OIDC_PROVIDERS).permitAll()
                        // The web frontend's files are public; it signs in through the API like any other client.
                        .requestMatchers(SecurityConfig::isFrontendRequest).permitAll()
                        .requestMatchers(HttpMethod.GET, OIDC_AUTHORIZE + "/*", OIDC_CALLBACK + "/*").permitAll()
                        // A user who must change their password may still do so, and see that they must.
                        .requestMatchers(CURRENT_USER, CURRENT_USER_PASSWORD).authenticated()
                        .anyRequest().access(new ActiveUserService()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(tokenDecoder)
                                .jwtAuthenticationConverter(new SonaJwtConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Signing in through an OIDC provider, with the authorization code flow. The provider's tokens are only
                // used here; the API takes Sona's own.
                .oauth2Login(login -> login
                        .clientRegistrationRepository(discoveryClient)
                        .authorizedClientRepository(authorizedClients)
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new SilentSignInResolver(discoveryClient)))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(OIDC_CALLBACK + "/*"))
                        .successHandler(oidcSignIn)
                        .failureHandler(oidcSignIn)
                        // The web frontend has the sign-in page, so Spring doesn't generate one.
                        .loginPage(properties.frontendUrl()))
                // An API request without a valid token gets an error response, not a redirect to a sign-in page.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    private static boolean isFrontendRequest(final HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod()) && !request.getRequestURI().startsWith(API + "/");
    }

    /**
     * Keeps the tokens a provider returns at sign-in in the sign-in's session, which ends once
     * {@link SignInHandler} has kept the refresh token. Spring's default would keep every signed-in user's tokens
     * in memory for good.
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
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
