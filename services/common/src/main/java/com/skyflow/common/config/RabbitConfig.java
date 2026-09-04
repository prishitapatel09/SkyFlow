package com.skyflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.common.event.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the shared RabbitMQ topology. Every service declares it identically and idempotently,
 * so start-up order does not matter.
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitConfig {

    @Bean
    public TopicExchange skyflowEventsExchange() {
        return new TopicExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange skyflowDeadLetterExchange() {
        return new DirectExchange(RabbitTopology.DLX, true, false);
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(RabbitTopology.NOTIFICATIONS_QUEUE)
                .deadLetterExchange(RabbitTopology.DLX)
                .deadLetterRoutingKey(RabbitTopology.NOTIFICATIONS_DLQ_KEY)
                .build();
    }

    @Bean
    public Queue notificationsDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Queue bookingPaymentsQueue() {
        return QueueBuilder.durable(RabbitTopology.BOOKING_PAYMENTS_QUEUE)
                .deadLetterExchange(RabbitTopology.DLX)
                .deadLetterRoutingKey(RabbitTopology.NOTIFICATIONS_DLQ_KEY)
                .build();
    }

    @Bean
    public Binding notificationsNotificationBinding() {
        return BindingBuilder.bind(notificationsQueue())
                .to(skyflowEventsExchange())
                .with(RabbitTopology.KEY_NOTIFICATION_ALL);
    }

    @Bean
    public Binding notificationsBookingBinding() {
        return BindingBuilder.bind(notificationsQueue())
                .to(skyflowEventsExchange())
                .with(RabbitTopology.KEY_BOOKING_ALL);
    }

    @Bean
    public Binding notificationsPaymentBinding() {
        return BindingBuilder.bind(notificationsQueue())
                .to(skyflowEventsExchange())
                .with(RabbitTopology.KEY_PAYMENT_ALL);
    }

    @Bean
    public Binding bookingPaymentsBinding() {
        return BindingBuilder.bind(bookingPaymentsQueue())
                .to(skyflowEventsExchange())
                .with(RabbitTopology.KEY_PAYMENT_ALL);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(notificationsDeadLetterQueue())
                .to(skyflowDeadLetterExchange())
                .with(RabbitTopology.NOTIFICATIONS_DLQ_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
