package com.ecommerce.chat.ws;

import com.ecommerce.chat.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Solo se declara el endpoint del WebSocket. El de los tickets es un controlador
 * MVC normal, asi que no necesita registro aqui.
 */
@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final TicketHandshakeInterceptor interceptor;
    private final String[] allowedOrigins;

    public ChatWebSocketConfig(ChatWebSocketHandler handler,
                               TicketService tickets,
                               @Value("${app.chat.allowed-origins}") String allowedOrigins) {
        this.handler = handler;
        this.interceptor = new TicketHandshakeInterceptor(tickets);
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/chat/ws")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
