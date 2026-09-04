package com.skyflow.notification.messaging;

import com.skyflow.common.event.RabbitTopology;
import com.skyflow.common.event.SkyflowEvent;
import com.skyflow.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The consumer that makes email asynchronous: producers publish an event and return, and delivery
 * happens here. This replaces the in-process nodemailer call the Express API awaited inline.
 *
 * <p>Retries and dead-lettering are configured on the listener container, so a throw from
 * {@code send} is redelivered a few times and then parked on
 * {@link RabbitTopology#NOTIFICATIONS_DLQ}.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService notificationService;

    public NotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitTopology.NOTIFICATIONS_QUEUE)
    public void onEvent(SkyflowEvent event) {
        log.debug("Handling {} event for {}", event.type(), event.recipientEmail());
        notificationService.send(event);
    }
}
