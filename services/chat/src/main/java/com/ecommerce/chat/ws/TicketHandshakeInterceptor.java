package com.ecommerce.chat.ws;

import com.ecommerce.chat.service.ChatIdentity;
import com.ecommerce.chat.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Resuelve el ticket durante el handshake y cuelga la identidad en los atributos
 * de la sesion.
 *
 * <p>Devolver false aborta el handshake con 403 antes de completar el upgrade, que
 * es lo que evita abrir un socket sin identidad. Rechazar despues, ya con la
 * conexion abierta, dejaria una sesion viva sin poder descartarla limpiamente.
 */
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TicketHandshakeInterceptor.class);
    private static final String USER_ID_ATTRIBUTE = "userId";
    private static final String USERNAME_ATTRIBUTE = "username";

    private final TicketService tickets;

    public TicketHandshakeInterceptor(TicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Optional<ChatIdentity> identity = tickets.consume(extractTicket(request));
        if (identity.isEmpty()) {
            log.debug("Handshake rechazado: ticket ausente, caducado o ya usado");
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, identity.get().userId());
        attributes.put(USERNAME_ATTRIBUTE, identity.get().username());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nada que limpiar: el ticket ya se consumio en beforeHandshake.
    }

    /**
     * Lee el parametro de la query a mano en vez de con UriComponentsBuilder, que
     * lanza IllegalArgumentException con entradas malformadas y cerraria el
     * handshake con un 500 en lugar de un 403.
     */
    private static String extractTicket(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if ("ticket".equals(key)) {
                return equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
