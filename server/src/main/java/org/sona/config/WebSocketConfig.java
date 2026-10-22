package org.sona.config;

import static org.sona.controller.ApiRoutes.SOCKET;
import static org.sona.controller.ApiRoutes.SOCKET_APP;
import static org.sona.controller.ApiRoutes.SOCKET_TOPICS;

import lombok.RequiredArgsConstructor;
import org.sona.auth.SocketAccessInterceptor;
import org.sona.controller.SocketErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Pushes updates to clients with STOMP over WebSocket. Sona runs as a single instance, so the broker is Spring's
 * in-memory one. Connections are only accepted from Sona's own origin.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SocketAccessInterceptor accessInterceptor;

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        registry.addEndpoint(SOCKET);
        registry.setErrorHandler(new SocketErrorHandler());
    }

    @Override
    public void configureMessageBroker(final MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(SOCKET_TOPICS);
        registry.setApplicationDestinationPrefixes(SOCKET_APP);
    }

    @Override
    public void configureClientInboundChannel(final ChannelRegistration registration) {
        registration.interceptors(accessInterceptor);
        // A client subscribes to a topic before asking what it missed, so its subscription must be in place by the time
        // the answer is read: one thread handles the frames in the order they came. Clients only connect and subscribe,
        // so one keeps up. Spring's setPreserveReceiveOrder would order them too, but it only logs a frame the
        // interceptor refuses, without sending the client an ERROR frame.
        registration.taskExecutor().corePoolSize(1).maxPoolSize(1);
    }
}
