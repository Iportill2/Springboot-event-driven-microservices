package com.ecommerce.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Esqueleto del canal: registra las sesiones abiertas y responde a {@code ping}
 * y {@code echo}. El enrutado de mensajes entre dos usuarios, la persistencia y
 * el historial entran en la siguiente iteracion.
 *
 * <p>El registro es por usuario y no por sesion para que los mensajes puedan
 * llegar a todas las pestanas abiertas de una misma persona.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER_ID_ATTRIBUTE = "userId";
    private static final String USERNAME_ATTRIBUTE = "username";

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        long userId = ((Number) attributes.get(USER_ID_ATTRIBUTE)).longValue();
        String username = (String) attributes.get(USERNAME_ATTRIBUTE);

        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("WebSocket abierto por {} ({})", username, userId);
        send(session, "connected", Map.of("userId", userId, "username", username));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ObjectNode root;
        try {
            root = (ObjectNode) MAPPER.readTree(message.getPayload());
        } catch (IOException e) {
            send(session, "error", Map.of("message", "El frame no es JSON válido"));
            return;
        }

        switch (root.path("type").asText("")) {
            case "ping" -> send(session, "pong", Map.of("at", Instant.now().toString()));
            case "echo" -> send(session, "echo", Map.of(
                    "received", root.path("payload"),
                    "at", Instant.now().toString()));
            default -> send(session, "error", Map.of(
                    "message", "Tipo no soportado: " + root.path("type").asText("")));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object rawUserId = session.getAttributes().get(USER_ID_ATTRIBUTE);
        if (!(rawUserId instanceof Number userId)) {
            return;
        }
        Set<WebSocketSession> userSessions = sessionsByUser.get(userId.longValue());
        if (userSessions == null) {
            return;
        }
        userSessions.remove(session);
        if (userSessions.isEmpty()) {
            sessionsByUser.remove(userId.longValue());
        }
        log.debug("WebSocket cerrado por {} ({}): {}", userId, status.getCode(), status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Fallo de transporte en el WebSocket: {}", exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void send(WebSocketSession session, String type, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            ObjectNode frame = MAPPER.createObjectNode();
            frame.put("type", type);
            frame.set("payload", MAPPER.valueToTree(payload));
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(frame)));
        } catch (IOException e) {
            // Una sesion que falla al escribir normalmente ya esta cerrada por el
            // otro extremo; no merece la pena propagar el error hacia arriba.
            log.debug("No se pudo enviar el frame '{}': {}", type, e.getMessage());
        }
    }
}
