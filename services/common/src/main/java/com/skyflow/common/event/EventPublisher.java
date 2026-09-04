package com.skyflow.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes domain events. Callers never block on delivery: a broker outage is logged and the
 * request continues, which is the point of moving email off the request path in the first place.
 */
@Component
@ConditionalOnClass(RabbitTemplate.class)
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(EventType type, String recipientEmail, Map<String, Object> data) {
        publish(SkyflowEvent.of(type, recipientEmail, data));
    }

    public void publish(SkyflowEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, event.type().routingKey(), event);
            log.debug("Published {} to {}", event.type(), event.type().routingKey());
        } catch (AmqpException ex) {
            log.error("Failed to publish {} event; continuing without it", event.type(), ex);
        }
    }
}
