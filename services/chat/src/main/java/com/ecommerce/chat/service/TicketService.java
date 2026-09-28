package com.ecommerce.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Tickets de un solo uso para el handshake del WebSocket.
 *
 * <p>El navegador no puede enviar cabeceras en un handshake, asi que el JWT no
 * llega al chat service. El flujo es: POST /api/chat/ws-ticket con el Bearer de
 * siempre (el gateway valida y reenvia X-User-Id), este servicio guarda la
 * identidad en Redis bajo un token opaco con TTL corto, y la conexion abre con
 * {@code /api/chat/ws?ticket=...}. El token se consume con getAndDelete, que es
 * atomico: un ticket repetido no vale nada, ni siquiera dentro de su ventana de
 * vida.
 */
@Service
public class TicketService {

    private static final String PREFIX = "chat:ticket:";
    /** Cortafuegos contra consultas arbitrarias a Redis con basura de la URL. */
    private static final int MAX_TICKET_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public TicketService(StringRedisTemplate redis,
                         @Value("${app.chat.ticket-ttl-seconds:30}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String mint(long userId, String username) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set(PREFIX + ticket, userId + ":" + username, Duration.ofSeconds(ttlSeconds));
        return ticket;
    }

    /**
     * Canjea el ticket por su identidad, de forma atomica y con el ultimo uso.
     *
     * @return vacio si el ticket no existe, ya se uso, caduco o esta mal formado.
     */
    public Optional<ChatIdentity> consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > MAX_TICKET_LENGTH) {
            return Optional.empty();
        }
        String payload = redis.opsForValue().getAndDelete(PREFIX + ticket);
        if (payload == null) {
            return Optional.empty();
        }
        int separator = payload.indexOf(':');
        if (separator < 1) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(payload.substring(0, separator));
            return Optional.of(new ChatIdentity(userId, payload.substring(separator + 1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
