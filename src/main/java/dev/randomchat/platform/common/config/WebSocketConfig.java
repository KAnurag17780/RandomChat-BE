package dev.randomchat.platform.common.config;

import dev.randomchat.platform.chat.handler.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configures the raw WebSocket endpoint at {@code /ws/chat}.
 * <p>
 * Allowed origins are loaded from {@code app.websocket.allowed-origins}.
 * The handshake is authenticated via the existing session; no separate
 * token is required because the browser sends the session cookie automatically.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(ChatWebSocketHandler chatHandler,
                           @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002,http://localhost:5173,http://localhost:5174}") String allowedOriginsStr) {
        this.chatHandler = chatHandler;
        this.allowedOrigins = java.util.Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat")
                .setAllowedOrigins(allowedOrigins);
    }
}
