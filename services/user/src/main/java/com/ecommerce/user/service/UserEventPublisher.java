package com.ecommerce.user.service;

import com.ecommerce.common.events.EventsTopics;
import com.ecommerce.common.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    public void userRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(EventsTopics.USER_EVENTS, event.eventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for user {}", event.userId(), ex);
                    } else {
                        log.info("UserRegisteredEvent published for user {} -> partition {} offset {}",
                                event.userId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}