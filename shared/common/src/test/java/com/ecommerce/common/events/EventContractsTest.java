package com.ecommerce.common.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractsTest {

    @Test
    void userRegisteredEventMapsEveryField() {
        UserRegisteredEvent event = UserRegisteredEvent.of(1L, "iker", "iker@example.com");

        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.username()).isEqualTo("iker");
        assertThat(event.email()).isEqualTo("iker@example.com");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void productCreatedEventMapsEveryField() {
        ProductCreatedEvent event = ProductCreatedEvent.of(9L, "Laptop", "SKU-1");

        assertThat(event.productId()).isEqualTo(9L);
        assertThat(event.name()).isEqualTo("Laptop");
        assertThat(event.sku()).isEqualTo("SKU-1");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void eventIdIsAValidUuid() {
        assertThat(UUID.fromString(UserRegisteredEvent.of(1L, "a", "a@b.c").eventId())).isNotNull();
        assertThat(UUID.fromString(ProductCreatedEvent.of(1L, "a", "SKU").eventId())).isNotNull();
    }

    @Test
    void eventIdIsUniquePerInstance() {
        // El eventId es la clave que usara el consumidor para deduplicar, asi que
        // dos eventos del mismo tipo nunca pueden compartirlo.
        String first = UserRegisteredEvent.of(1L, "a", "a@b.c").eventId();
        String second = UserRegisteredEvent.of(1L, "a", "a@b.c").eventId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void topicNamesAreDeclaredAndDistinct() {
        assertThat(EventsTopics.USER_EVENTS).isEqualTo("user-events");
        assertThat(EventsTopics.PRODUCT_EVENTS).isEqualTo("product-events");
        assertThat(EventsTopics.ORDER_EVENTS).isEqualTo("order-events");
        assertThat(EventsTopics.INVENTORY_EVENTS).isEqualTo("inventory-events");
    }
}
