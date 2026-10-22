package org.sona.controller;

import java.util.List;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Opens STOMP connections to Sona's socket as a browser would, with the access token in the CONNECT frame.
 */
public final class SocketClient {

    private final WebSocketStompClient stompClient;
    private final String url;

    public SocketClient(final int port) {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Sona's messages are JSON; its ERROR frames are plain text.
        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));
        url = "ws://localhost:" + port + ApiRoutes.SOCKET;
    }

    public SocketConnection connect(final String accessToken) {
        final var headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return connectWithHeaders(headers);
    }

    public SocketConnection connectAnonymously() {
        return connectWithHeaders(new StompHeaders());
    }

    private SocketConnection connectWithHeaders(final StompHeaders headers) {
        final var connection = new SocketConnection();
        stompClient.connectAsync(url, new WebSocketHttpHeaders(), headers, connection);
        return connection;
    }
}
