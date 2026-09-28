package com.ecommerce.product.service;

import com.ecommerce.common.events.EventsTopics;
import com.ecommerce.common.events.ProductCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProductEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    public void productCreated(ProductCreatedEvent event) {
        kafkaTemplate.send(EventsTopics.PRODUCT_EVENTS, event.eventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ProductCreatedEvent for product {}", event.productId(), ex);
                    } else {
                        log.info("ProductCreatedEvent published for product {} -> partition {} offset {}",
                                event.productId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}