package com.skyflow.common.event;

/** Event kinds carried by {@link SkyflowEvent}, each with the routing key it is published under. */
public enum EventType {

    REMINDER(RabbitTopology.KEY_REMINDER),
    BOOKING_CONFIRMED(RabbitTopology.KEY_BOOKING_CONFIRMED),
    BOOKING_CANCELLED(RabbitTopology.KEY_BOOKING_CANCELLED),
    PAYMENT_SUCCEEDED(RabbitTopology.KEY_PAYMENT_SUCCEEDED),
    PAYMENT_FAILED(RabbitTopology.KEY_PAYMENT_FAILED),
    PAYMENT_REFUNDED(RabbitTopology.KEY_PAYMENT_REFUNDED),
    CITY_CREATED(RabbitTopology.KEY_CITY_CREATED);

    private final String routingKey;

    EventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
