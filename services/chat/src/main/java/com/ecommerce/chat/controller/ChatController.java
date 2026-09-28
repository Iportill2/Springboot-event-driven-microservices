package com.ecommerce.chat.controller;

import com.ecommerce.chat.service.TicketService;
import com.ecommerce.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emite los tickets que despues consume el handshake del WebSocket.
 *
 * <p>No se registra ningun filtro de seguridad aqui: la identidad llega en
 * X-User-Id, que el gateway solo anade tras validar el JWT. Este endpoint sigue
 * exigiendo esa cabecera, asi que sin pasar por el gateway la peticion falla.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final TicketService tickets;

    public ChatController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/ws-ticket")
    public ResponseEntity<ApiResponse<WsTicketResponse>> createTicket(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "X-Username", required = false) String username) {
        String ticket = tickets.mint(userId, username == null ? "desconocido" : username);
        return ResponseEntity.ok(ApiResponse.ok(new WsTicketResponse(ticket, tickets.ttlSeconds())));
    }
}
