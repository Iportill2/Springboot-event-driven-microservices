package com.ecommerce.chat.service;

/** Identidad que el gateway extrae del JWT y que el ticket transporta hasta el handshake. */
public record ChatIdentity(long userId, String username) {
}
