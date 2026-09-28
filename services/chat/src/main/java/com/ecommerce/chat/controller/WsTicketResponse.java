package com.ecommerce.chat.controller;

/** Ticket opaco mas el tiempo que le queda de vida. */
public record WsTicketResponse(String ticket, long expiresInSeconds) {
}
