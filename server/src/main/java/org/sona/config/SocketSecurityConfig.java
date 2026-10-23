package org.sona.config;

import static org.sona.controller.ApiRoutes.INGEST_PROGRESS;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import org.sona.auth.ActiveUserService;
import org.sona.auth.Permission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * Checks each STOMP message against the permissions of the user {@link org.sona.auth.SocketAuthenticationInterceptor}
 * authenticated the connection as. Sona only pushes, so clients may connect and subscribe, and send nothing.
 */
@Configuration
@EnableWebSocketSecurity
public class SocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            final MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages.nullDestMatcher()
                .authenticated()
                .simpSubscribeDestMatchers(INGEST_PROGRESS)
                .access(activeUserWith(Permission.LIBRARY_UPLOAD))
                .anyMessage()
                .denyAll()
                .build();
    }

    /**
     * Spring Security checks a CSRF token on CONNECT, which guards connections that a session cookie authenticates.
     * Sona's are authenticated by the access token in the CONNECT frame, which another site's page can't send, so
     * there's nothing to guard. Spring looks this interceptor up by name, so a bean of the same name replaces it.
     */
    @Bean
    public ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {};
    }

    private static AuthorizationManager<MessageAuthorizationContext<?>> activeUserWith(final Permission permission) {
        return allOf(new ActiveUserService<>(), hasAuthority(permission.getAuthority()));
    }
}
